import SwiftUI
import CoreGraphics
import Shared

#if os(iOS)
import UIKit
typealias PlatformViewRepresentable = UIViewRepresentable
typealias PlatformView = UIView
#else
import AppKit
typealias PlatformViewRepresentable = NSViewRepresentable
typealias PlatformView = NSView
#endif

struct ScoreCanvasViewRepresentable: PlatformViewRepresentable {
    var pages: [LayoutPage]
    
    #if os(iOS)
    func makeUIView(context: Context) -> ScoreCanvasView {
        let view = ScoreCanvasView(frame: .zero)
        view.backgroundColor = .white
        return view
    }
    
    func updateUIView(_ uiView: ScoreCanvasView, context: Context) {
        uiView.updateLayout(pages: pages)
    }
    #else
    func makeNSView(context: Context) -> ScoreCanvasView {
        let view = ScoreCanvasView(frame: .zero)
        return view
    }
    
    func updateNSView(_ nsView: ScoreCanvasView, context: Context) {
        nsView.updateLayout(pages: pages)
    }
    #endif
}

class ScoreCanvasView: PlatformView {
    private var pages: [LayoutPage] = []
    
    // Hardcoded Bravura font loading
    private lazy var bravuraFont: CTFont? = {
        guard let url = Bundle.main.url(forResource: "Bravura", withExtension: "otf"),
              let provider = CGDataProvider(url: url as CFURL),
              let cgFont = CGFont(provider) else {
            return nil
        }
        return CTFontCreateWithGraphicsFont(cgFont, 24.0, nil, nil)
    }()
    
    #if os(iOS)
    override class var layerClass: AnyClass { CATiledLayer.self }
    #else
    // macOS layer setup is done in init
    #endif
    
    func updateLayout(pages: [LayoutPage]) {
        self.pages = pages
        #if os(iOS)
        setNeedsDisplay()
        #else
        setNeedsDisplay(bounds)
        #endif
    }
    
    #if os(iOS)
    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        drawScore(in: ctx)
    }
    #else
    override func draw(_ dirtyRect: NSRect) {
        guard let ctx = NSGraphicsContext.current?.cgContext else { return }
        
        // macOS CGContext has flipped coordinates by default relative to UIKit,
        // we must flip it to draw properly.
        ctx.saveGState()
        ctx.translateBy(x: 0, y: bounds.height)
        ctx.scaleBy(x: 1.0, y: -1.0)
        
        drawScore(in: ctx)
        ctx.restoreGState()
    }
    #endif
    
    private func drawScore(in ctx: CGContext) {
        // Very basic coordinate transform mapping 1 staff space to 10 pixels
        ctx.scaleBy(x: 10.0, y: 10.0)
        ctx.translateBy(x: 5.0, y: 10.0)
        
        for page in pages {
            for system in page.systems {
                for glyph in system.glyphs {
                    // For now, Kotlin's GlyphDrawCommand needs bridging, 
                    // this assumes standard structure
                    if let smuflGlyph = glyph as? GlyphDrawCommand.SmuflGlyph {
                        drawMusicText(smuflGlyph.codepoint, x: smuflGlyph.x, y: smuflGlyph.y, ctx: ctx)
                    }
                }
            }
        }
    }
    
    private func drawMusicText(_ codepoint: String, x: Double, y: Double, ctx: CGContext) {
        guard let font = bravuraFont else { return }
        
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font
        ]
        
        let string = NSAttributedString(string: codepoint, attributes: attributes)
        let line = CTLineCreateWithAttributedString(string)
        
        ctx.saveGState()
        ctx.textPosition = CGPoint(x: CGFloat(x), y: CGFloat(y))
        CTLineDraw(line, ctx)
        ctx.restoreGState()
    }
}
