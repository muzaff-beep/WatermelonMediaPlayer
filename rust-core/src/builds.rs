// rust-core/build.rs
fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=src/");
    // Android NDK requires linking against libc++ for certain C++ dependencies
    if std::env::var("TARGET").map_or(false, |t| t.contains("android")) {
        println!("cargo:rustc-link-lib=c++");
    }
}