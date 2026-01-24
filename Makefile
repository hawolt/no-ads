# no-ads Makefile
# Cross-platform build helper for Windows (via Git Bash/MinGW) and macOS

.PHONY: help build run clean package-extension build-rust install-deps all

# Default target
help:
	@echo "no-ads Build System"
	@echo "==================="
	@echo ""
	@echo "Available targets:"
	@echo "  make build            - Build the Java local-server"
	@echo "  make run              - Run the local-server (requires Java 17+)"
	@echo "  make build-run        - Build and run in one step"
	@echo "  make clean            - Clean all build artifacts"
	@echo "  make package-extension - Package the Chrome extension as a zip"
	@echo "  make build-rust       - Build the Windows executable (Windows only)"
	@echo "  make install-deps     - Check/display required dependencies"
	@echo "  make all              - Build everything"
	@echo ""
	@echo "Prerequisites:"
	@echo "  - Java 17+ (JDK)"
	@echo "  - Maven 3.6+"
	@echo "  - Rust (for Windows .exe only)"
	@echo ""

# Build the Java local-server
build:
	@echo "Building local-server..."
	cd local-server && mvn clean package -DskipTests
	@echo "Build complete: local-server/target/application.jar"

# Run the local-server
run:
	@echo "Starting local-server on http://localhost:61616"
	@if [ -f local-server/target/application.jar ]; then \
		JAVA_VERSION=$$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1); \
		if [ "$$JAVA_VERSION" = "1" ]; then JAVA_VERSION=8; fi; \
		if [ "$$JAVA_VERSION" -lt 17 ] 2>/dev/null; then \
			echo ""; \
			echo "ERROR: Java 17+ required, but you have Java $$JAVA_VERSION"; \
			echo ""; \
			echo "Install Java 17 with Homebrew:"; \
			echo "  brew install openjdk@17"; \
			echo "  sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk"; \
			echo ""; \
			echo "Or download from: https://adoptium.net/"; \
			exit 1; \
		fi; \
		java -jar local-server/target/application.jar; \
	else \
		echo "Error: application.jar not found. Run 'make build' first."; \
		exit 1; \
	fi

# Build and run in one step
build-run: build run

# Clean all build artifacts
clean:
	@echo "Cleaning build artifacts..."
	cd local-server && mvn clean
	@if [ -d rust-wrapper/target ]; then \
		cd rust-wrapper && cargo clean; \
	fi
	rm -f chromium-extension.zip
	@echo "Clean complete."

# Package the Chrome extension
package-extension:
	@echo "Packaging Chrome extension..."
	@# Copy shared files from extension/ to chrome-extension/ if they exist
	@if [ -d extension ]; then \
		cp -f extension/hls.min.js chrome-extension/ 2>/dev/null || true; \
		cp -f extension/icon*.png chrome-extension/ 2>/dev/null || true; \
	fi
	@# Create zip archive
	@if command -v zip >/dev/null 2>&1; then \
		cd chrome-extension && zip -r ../chromium-extension.zip . -x "*.DS_Store"; \
	else \
		echo "Note: 'zip' command not found. Creating archive with tar..."; \
		tar -cvf chromium-extension.tar -C chrome-extension .; \
	fi
	@echo "Extension packaged: chromium-extension.zip"

# Build the Rust wrapper (Windows executable)
build-rust:
	@echo "Building Rust wrapper..."
	@echo "Note: This creates a Windows .exe and requires:"
	@echo "  - application.jar copied to rust-wrapper/"
	@echo "  - jre.zip (JRE 17) copied to rust-wrapper/"
	@if [ ! -f rust-wrapper/application.jar ]; then \
		echo "Copying application.jar to rust-wrapper/..."; \
		cp local-server/target/application.jar rust-wrapper/ 2>/dev/null || \
		(echo "Error: Build the Java server first with 'make build'" && exit 1); \
	fi
	cd rust-wrapper && cargo build --release
	@echo "Build complete: rust-wrapper/target/release/local-server.exe"

# Check dependencies
install-deps:
	@echo "Checking dependencies..."
	@echo ""
	@echo "Java:"
	@java -version 2>&1 | head -1 || echo "  NOT FOUND - Install Java 17+ from https://adoptium.net/"
	@echo ""
	@echo "Maven:"
	@mvn -version 2>&1 | head -1 || echo "  NOT FOUND - Install Maven from https://maven.apache.org/"
	@echo ""
	@echo "Rust (optional, for Windows .exe):"
	@rustc --version 2>&1 || echo "  NOT FOUND - Install from https://rustup.rs/"
	@echo ""

# Build everything
all: build package-extension
	@echo ""
	@echo "All builds complete!"
	@echo "  - Java server: local-server/target/application.jar"
	@echo "  - Extension:   chromium-extension.zip"
