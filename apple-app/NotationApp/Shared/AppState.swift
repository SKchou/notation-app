import Foundation
import Combine
import Shared // Kotlin Multiplatform Module

@MainActor
class AppState: ObservableObject {
    @Published var score: Score
    @Published var currentPages: [LayoutPage] = []
    
    private let layoutEngine: LayoutEngine
    private let commandExecutor: CommandExecutor
    private let metrics: GlyphMetrics
    
    init() {
        // Initialize default score from Kotlin core
        self.score = Score.Companion.shared.create(
            title: "Untitled Score",
            parts: [],
            measureCount: 1
        )
        
        // Use the DefaultGlyphMetrics exported from KMP
        self.metrics = DefaultGlyphMetrics()
        self.layoutEngine = LayoutEngine(engravingRules: EngravingRules.Companion.shared.DEFAULT, glyphMetrics: self.metrics)
        self.commandExecutor = CommandExecutor.shared
        
        recalculateLayout()
    }
    
    func dispatch(command: ScoreCommand) {
        let result = commandExecutor.execute(score: score, command: command)
        self.score = result.newScore
        // TODO: Push result.reverseCommand to an Undo stack
        recalculateLayout()
    }
    
    private func recalculateLayout() {
        // Assume default page setup
        let setup = PageSetup()
        self.currentPages = layoutEngine.layout(score: score, pageSetup: setup)
    }
}

