import SwiftUI

struct ScoreEditorView: View {
    @EnvironmentObject var appState: AppState
    
    var body: some View {
        #if os(iOS)
        NavigationView {
            editorContent
                .navigationTitle("Score Editor")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItemGroup(placement: .navigationBarTrailing) {
                        Button(action: { /* Playback */ }) {
                            Image(systemName: "play.fill")
                        }
                    }
                }
        }
        #else
        editorContent
            .navigationTitle("Score Editor")
            .toolbar {
                ToolbarItemGroup {
                    Button(action: { /* Playback */ }) {
                        Image(systemName: "play.fill")
                    }
                }
            }
        #endif
    }
    
    private var editorContent: some View {
        HStack {
            // Sidebar for note palette
            VStack(spacing: 16) {
                Text("Tools")
                    .font(.headline)
                
                Button("Quarter Note") { /* Set entry mode */ }
                Button("Eighth Note") { /* Set entry mode */ }
                Button("Rest") { /* Set entry mode */ }
                
                Spacer()
            }
            .frame(width: 150)
            .padding()
            .background(Color(.windowBackgroundColor).opacity(0.5))
            
            // Main Canvas Area
            ScrollView([.horizontal, .vertical]) {
                ScoreCanvasViewRepresentable(pages: appState.currentPages)
                    .frame(width: 800, height: 1200) // Hardcoded page size for v1
                    .background(Color.white)
                    .shadow(radius: 5)
                    .padding()
            }
            .background(Color.gray.opacity(0.1))
        }
    }
}

// macOS compatibility polyfill for background color
#if os(iOS)
extension UIColor {
    static var windowBackgroundColor: UIColor { .systemGroupedBackground }
}
#else
extension NSColor {
    static var windowBackgroundColor: NSColor { .windowBackgroundColor }
}
extension Color {
    init(_ nsColor: NSColor) {
        self.init(nsColor: nsColor)
    }
}
#endif
