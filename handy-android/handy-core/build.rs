fn main() {
    #[cfg(target_os = "android")]
    {
        // OpenSLES for older Android API levels
        println!("cargo:rustc-link-lib=OpenSLES");
        // AAudio for Android 8.0+ (API 26)
        println!("cargo:rustc-link-lib=aaudio");
    }
}
