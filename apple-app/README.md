# Notation App — Apple UI

This directory contains the SwiftUI and CoreGraphics frontend for the Notation App, targeting **macOS 14+** and **iPadOS 17+**. 

The UI layer delegates all heavy lifting (engraving layout, score state mutations, rendering coordinates) to the `Shared` Kotlin Multiplatform framework.

## Project Setup Instructions (For macOS)

Since you are currently working on a Windows machine, the Kotlin compiler cannot generate the required `.xcframework` binaries for Apple targets. Follow these instructions on your Mac to build and run the app.

### 1. Build the Kotlin Multiplatform Framework
1. Copy this entire repository (`notation-app`) to your Mac.
2. Open a terminal in the root directory.
3. Build the XCFramework:
   ```bash
   ./gradlew :shared:assembleXCFramework
   ```
4. The output framework will be located at `shared/build/XCFrameworks/debug/Shared.xcframework`.

### 2. Configure the Xcode Project
1. Open Xcode on your Mac.
2. Select **File > New > Project**, choose **iOS > App** or **macOS > App**.
3. Name it `NotationApp` (Interface: SwiftUI, Language: Swift).
4. Save the new Xcode project directly into this `apple-app/` directory (replacing the folder if necessary, but keep the `.swift` files we generated).
5. Drag the Swift files we wrote (`NotationApp.swift`, `AppState.swift`, `ScoreCanvasView.swift`, `AudioEngine.swift`, `ScoreEditorView.swift`) into your Xcode Project navigator, making sure they are added to your target.
6. **Link the XCFramework**:
   - Go to your app target settings -> **General** -> **Frameworks, Libraries, and Embedded Content**.
   - Click the **+** button, select **Add Other... -> Add Files**.
   - Navigate to `shared/build/XCFrameworks/debug/Shared.xcframework` and select it.
   - Ensure it is set to **Embed & Sign**.

### 3. Add Assets
1. Download the [Bravura font (Bravura.otf)](https://github.com/steinbergmedia/bravura) and drag it into your Xcode project. Ensure it's added to the target.
2. (iOS only) Add the font to your `Info.plist` under `Fonts provided by application`.

### 4. Build and Run
Select your iPad or Mac target in Xcode and hit **Cmd + R** to launch the vector-engraved score editor!
