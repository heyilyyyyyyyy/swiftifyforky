import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Swiftify

Item {
    id: root

    property int activeTab: 0 // 0: Home, 1: Search, 2: Library, 3: Settings
    property bool playerExpanded: false

    // Main content stack
    StackLayout {
        anchors.top: parent.top
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: miniPlayerBar.top
        currentIndex: root.activeTab

        // 0: Home Feed
        Item {
            id: homePage
            Text {
                anchors.centerIn: parent
                text: qsTr("Home")
                color: "white"
                font.pixelSize: 22
            }
        }

        // 1: Search
        Item {
            id: searchPage
            Text {
                anchors.centerIn: parent
                text: qsTr("Search")
                color: "white"
                font.pixelSize: 22
            }
        }

        // 2: Library
        Item {
            id: libraryPage
            Text {
                anchors.centerIn: parent
                text: qsTr("Your Library")
                color: "white"
                font.pixelSize: 22
            }
        }

        // 3: Settings
        Item {
            id: settingsPage
            Text {
                anchors.centerIn: parent
                text: qsTr("Settings")
                color: "white"
                font.pixelSize: 22
            }
        }
    }

    // Mini Player bar (docked above bottom nav)
    Rectangle {
        id: miniPlayerBar
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: bottomNavBar.top
        anchors.margins: 8
        height: 56
        radius: 12
        color: Qt.rgba(0.18, 0.18, 0.2, 0.95)

        MouseArea {
            anchors.fill: parent
            onClicked: root.playerExpanded = true
        }

        RowLayout {
            anchors.fill: parent
            anchors.leftMargin: 12
            anchors.rightMargin: 12
            spacing: 12

            Rectangle {
                width: 40
                height: 40
                radius: 6
                color: "#333"
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 2
                Text {
                    text: qsTr("Track Name")
                    color: "white"
                    font.bold: true
                    font.pixelSize: 14
                    elide: Text.ElideRight
                    Layout.fillWidth: true
                }
                Text {
                    text: qsTr("Artist Name")
                    color: "#aaa"
                    font.pixelSize: 12
                    elide: Text.ElideRight
                    Layout.fillWidth: true
                }
            }

            ToolButton {
                text: "▶"
            }
        }
    }

    // Bottom Navigation Bar
    Rectangle {
        id: bottomNavBar
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 64
        color: "#121214"

        RowLayout {
            anchors.fill: parent

            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                MouseArea {
                    anchors.fill: parent
                    onClicked: root.activeTab = 0
                }
                Text {
                    anchors.centerIn: parent
                    text: qsTr("Home")
                    color: root.activeTab === 0 ? "#1db954" : "#888"
                }
            }

            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                MouseArea {
                    anchors.fill: parent
                    onClicked: root.activeTab = 1
                }
                Text {
                    anchors.centerIn: parent
                    text: qsTr("Search")
                    color: root.activeTab === 1 ? "#1db954" : "#888"
                }
            }

            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                MouseArea {
                    anchors.fill: parent
                    onClicked: root.activeTab = 2
                }
                Text {
                    anchors.centerIn: parent
                    text: qsTr("Library")
                    color: root.activeTab === 2 ? "#1db954" : "#888"
                }
            }

            Item {
                Layout.fillWidth: true
                Layout.fillHeight: true
                MouseArea {
                    anchors.fill: parent
                    onClicked: root.activeTab = 3
                }
                Text {
                    anchors.centerIn: parent
                    text: qsTr("Settings")
                    color: root.activeTab === 3 ? "#1db954" : "#888"
                }
            }
        }
    }
}
