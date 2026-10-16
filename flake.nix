{
  description = "Volta — Android photosphere capture app (dev environment)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachSystem [ "x86_64-linux" "aarch64-darwin" "x86_64-darwin" ] (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        # Keep in sync with app/build.gradle.kts (compileSdk / targetSdk).
        buildToolsVersion = "36.0.0";
        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [
            "35"
            "37.0"
          ];
          buildToolsVersions = [ buildToolsVersion ];
          includeEmulator = false;
          includeSystemImages = false;
          includeNDK = false;
        };
        androidSdk = android.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";

        jdk = pkgs.jdk17;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            androidSdk
            pkgs.git
          ];

          JAVA_HOME = jdk.home;
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;

          # Use the Nix-provided aapt2 instead of the one Gradle downloads from Maven.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

          shellHook = ''
            echo "Volta dev shell — JDK $(java -version 2>&1 | head -1), ANDROID_HOME=$ANDROID_HOME"
          '';
        };

        formatter = pkgs.nixfmt-rfc-style;
      }
    );
}
