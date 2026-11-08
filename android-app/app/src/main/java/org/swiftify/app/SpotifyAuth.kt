package org.swiftify.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

// ─── Spotify OAuth constants matching Swiftify desktop ─────────────────────────

private const val CLIENT_ID = "65b708073fc0480ea92a077233ca87bd"
private const val REDIRECT_PORT = 8898
private const val REDIRECT_URI = "http://127.0.0.1:8898/login"
private const val SCOPES =
    "streaming user-read-playback-state user-read-recently-played user-library-read " +
        "user-library-modify playlist-read-private playlist-read-collaborative " +
        "playlist-modify-private playlist-modify-public user-follow-read user-read-private"

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(64)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

/**
 * Owns the PKCE login flow, the loopback redirect server and the token exchange.
 * It reports state changes back to the Activity through callbacks so the UI
 * remains the single owner of observable state.
 */
class SpotifyAuth(
    private val context: Context,
    private val scope: CoroutineScope,
    private val currentAuthState: () -> AuthState,
    private val onAuthState: (AuthState) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onToken: (String) -> Unit
) {
    private var codeVerifier: String = ""
    private var oauthStateParam: String = ""
    private var authServer: ServerSocket? = null

    /** Re-validates the stored token and pushes the result through [onToken]. */
    fun refreshAccessToken(force: Boolean = false) {
        scope.launch {
            val token = SpotifyApi.getValidToken(context, forceRefresh = force)
            withContext(Dispatchers.Main) {
                if (token != null) {
                    onToken(token)
                }
            }
        }
    }

    fun startLogin(activity: Activity) {
        try {
            codeVerifier = generateCodeVerifier()
            val challenge = generateCodeChallenge(codeVerifier)
            oauthStateParam = UUID.randomUUID().toString()

            startLocalRedirectServer()

            val url = Uri.Builder()
                .scheme("https")
                .authority("accounts.spotify.com")
                .path("/authorize")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("state", oauthStateParam)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()

            onAuthState(AuthState.Connecting)
            onStatus("Waiting for authorization in browser...")
            CustomTabsIntent.Builder().build().launchUrl(activity, url)
        } catch (e: Exception) {
            Log.e("Auth", "Failed to start login", e)
            onStatus("Login init failed: ${e.message}")
        }
    }

    fun close() {
        try {
            authServer?.close()
        } catch (_: Exception) {
        }
        authServer = null
    }

    private fun startLocalRedirectServer() {
        close()

        scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(REDIRECT_PORT, 1, InetAddress.getByName("127.0.0.1"))
                server.soTimeout = 120_000 // 2 min timeout
                authServer = server
                Log.d("AuthServer", "Listening on http://127.0.0.1:$REDIRECT_PORT/login")

                val socket = server.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: ""
                Log.d("AuthServer", "Request: $requestLine")

                // Parse: GET /login?code=... HTTP/1.1
                val urlPart = requestLine.split(" ").getOrNull(1) ?: ""
                val uri = Uri.parse("http://127.0.0.1$urlPart")
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error")
                val returnedState = uri.getQueryParameter("state")
                val isStateValid = returnedState != null && returnedState == oauthStateParam

                val responseHtml = if (isStateValid && code != null) {
                    """
                    <!doctype html><html><head><meta charset="utf-8">
                    <title>Swiftify</title>
                    <style>body{font-family:sans-serif;background:#161413;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;}h2{color:#F2A385;}</style>
                    </head><body><div><h2>✓ Signed In to Swiftify!</h2><p>You can return to the Swiftify app now.</p></div></body></html>
                    """.trimIndent()
                } else {
                    """
                    <!doctype html><html><head><meta charset="utf-8">
                    <title>Swiftify</title>
                    <style>body{font-family:sans-serif;background:#161413;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;}h2{color:#FF6B6B;}</style>
                    </head><body><div><h2>✕ Authentication Failed</h2><p>${if (!isStateValid) "Invalid security state (CSRF verification failed)" else (error ?: "Cancelled")}</p></div></body></html>
                    """.trimIndent()
                }

                val responseBytes = responseHtml.toByteArray(Charsets.UTF_8)
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                out.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                out.write("Content-Length: ${responseBytes.size}\r\n".toByteArray())
                out.write("Connection: close\r\n\r\n".toByteArray())
                out.write(responseBytes)
                out.flush()
                socket.close()
                server.close()
                authServer = null

                withContext(Dispatchers.Main) {
                    if (!isStateValid) {
                        Log.e("AuthServer", "Auth failed: state mismatch (expected $oauthStateParam, got $returnedState)")
                        onAuthState(AuthState.LoggedOut)
                        onStatus("Security error: invalid OAuth state parameter")
                        Toast.makeText(context, "Authentication rejected: invalid state", Toast.LENGTH_LONG).show()
                    } else if (code != null) {
                        Log.d("AuthServer", "Captured authorization code with validated CSRF state ✓")
                        onStatus("Exchanging code for token...")
                        scope.launch { exchangeCodeForToken(code) }
                    } else {
                        Log.e("AuthServer", "Auth failed or cancelled: $error")
                        onAuthState(AuthState.LoggedOut)
                        onStatus("Auth cancelled: $error")
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthServer", "Server exception", e)
                withContext(Dispatchers.Main) {
                    if (currentAuthState() == AuthState.Connecting) {
                        onAuthState(AuthState.LoggedOut)
                        onStatus("Auth timeout or cancelled")
                    }
                }
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String) {
        try {
            val token = withContext(Dispatchers.IO) {
                val conn = URL("https://accounts.spotify.com/api/token")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.doOutput = true
                val body = "grant_type=authorization_code" +
                    "&code=${Uri.encode(code)}" +
                    "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                    "&client_id=$CLIENT_ID" +
                    "&code_verifier=${Uri.encode(codeVerifier)}"
                conn.outputStream.write(body.toByteArray())
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(response)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                SecureStorage.saveTokens(context, accessToken, refreshToken, expiresIn)
                accessToken
            }
            Log.d("Auth", "Access token acquired (${token.length} chars)")
            onToken(token)
            onStatus("Connecting audio core...")
            SpotifyBridge.connect(token)
        } catch (e: Exception) {
            Log.e("Auth", "Token exchange failed", e)
            onAuthState(AuthState.LoggedOut)
            onStatus("Token exchange failed: ${e.message}")
        }
    }
}
