import Foundation
import AVFoundation
import Shared

class ScoreAudioEngine {
    private let engine = AVAudioEngine()
    private let sampler = AVAudioUnitSampler()
    
    init() {
        setupEngine()
    }
    
    private func setupEngine() {
        engine.attach(sampler)
        engine.connect(sampler, to: engine.mainMixerNode, format: nil)
        
        do {
            try engine.start()
            // Load standard Apple DLS bank (fallback standard MIDI sounds)
            guard let bankURL = Bundle.main.url(forResource: "gs_instruments", withExtension: "dls") else {
                print("Default sound bank not found in bundle. Using system fallback.")
                return
            }
            try sampler.loadInstrument(at: bankURL)
        } catch {
            print("Failed to start audio engine: \(error)")
        }
    }
    
    /// Translates a Kotlin AudioEvent to an immediate MIDI message
    /// In a real app, this would use a timeline scheduler
    func playEvent(_ event: AudioEvent) {
        if let noteOn = event as? AudioEvent.NoteOn {
            sampler.startNote(UInt8(noteOn.midiNote), withVelocity: UInt8(noteOn.velocity), onChannel: UInt8(noteOn.channel))
        } else if let noteOff = event as? AudioEvent.NoteOff {
            sampler.stopNote(UInt8(noteOff.midiNote), onChannel: UInt8(noteOff.channel))
        }
    }
}
