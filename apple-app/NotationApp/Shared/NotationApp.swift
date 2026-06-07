import SwiftUI
import Shared // The Kotlin Multiplatform Framework

@main
struct NotationApp: App {
    @StateObject private var appState = AppState()
    
    var body: some Scene {
        WindowGroup {
            ScoreEditorView()
                .environmentObject(appState)
        }
    }
}
