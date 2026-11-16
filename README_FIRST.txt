CAPRI AI — ANDROID PROJECT STARTER
==================================

What this is
------------
A dedicated Android app project for running Capri's GGUF model locally.
It uses llama.cpp through the llama.kt Android binding. It does NOT include
the model weights. On first launch, import your own Capri .gguf file.

Target device
-------------
Redmi Note 9 Pro Max, 6 GB RAM, arm64-v8a.
The app starts with CPU inference, 2048 context and 384 max response tokens.
This is intentionally conservative for the target phone. You can adjust the
response token limit and temperature in Settings.

IMPORTANT STATUS
----------------
This ZIP is an Android Studio source project plus a Colab build notebook.
It is NOT a precompiled APK yet. The current ChatGPT workspace does not have
the Android SDK/NDK build toolchain, so I cannot honestly label an APK as
built or tested. Use the included Build_Capri_APK.ipynb in Google Colab to
compile a debug APK, then download it from the notebook.

Build steps (Google Colab)
--------------------------
1. Upload this ZIP to Colab.
2. Open Build_Capri_APK.ipynb from this ZIP (or upload it separately).
3. Run the notebook cells in order.
4. When asked, select CapriAI_Android.zip.
5. Wait for Gradle + native llama.cpp compilation to finish.
6. Download app-debug.apk from the final cell.
7. Install it on your phone. Android may ask you to allow installs from the
   browser/files app you used.

First launch
------------
1. Tap IMPORT GGUF.
2. Select your Capri GGUF file (the model file is not packaged in the APK).
3. Wait for the file copy to finish.
4. Tap LOAD CAPRI.
5. Start chatting.

Notes
-----
- This first build uses CPU inference only. Snapdragon 720G's Adreno 618 is
  not assumed to have a supported GPU backend in this starter.
- Keep at least 2–3 GB of free storage before importing the GGUF.
- A 1.7B Q4_K_M model is the intended model class; actual performance depends
  on the exact GGUF, Android version, free RAM and thermals.
- Chat history and settings are stored locally in the app's private data.
- Uninstalling the app deletes its private model copy and chat history.
- The app does not need an account or network permission for inference.
- This is a starter implementation. It has not yet been compiled on a physical
  Redmi Note 9 Pro Max, so the first Colab build may expose dependency/API
  version adjustments that need fixing.

Engine reference:
https://github.com/tensai-app/llama.kt
Android GGUF reference:
https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
