# Stream Deployer

Stream Deployer creates Kubernetes manifests and Mermaid diagrams from Spring Cloud Data Flow stream definitions.
It resolves stream bindings and injects communication channels and groups into container arguments.

## Requirements

The project requires the following software:
- Java 25 (SDKMAN or compatible JDK)
- GraalVM with `native-image` (for native compilation only)

## Running the Program

Run the application with Gradle or with the compiled binary.

### Run with Gradle

```bash
./gradlew run --args="--definition=src/test/resources/time-logger.json --properties=src/test/resources/time-logger.properties --metadata=src/test/resources/stream-metadata.properties"
```

### Command-Line Options

The command syntax is:

```text
stream-deployer [OPTIONS] -d=<definitionFile>
```

The table below shows all command-line options:

| Option | Required | Description |
| :--- | :--- | :--- |
| `-d, --definition=<file>` | Yes | Path to JSON file with stream definitions. |
| `-p, --properties=<file>` | No* | Path to properties file with deployment properties. |
| `-m, --metadata=<file>` | No* | Path to properties file with container image metadata. |
| `-o, --output=<file>` | No | Path to output Kubernetes YAML manifest file. |
| `--diagram[=<file>]` | No | Path to output Mermaid diagram file (`.mmd`). |
| `-h, --help` | No | Show help message and exit. |
| `-V, --version` | No | Show version information and exit. |

\* Provide `--properties` and `--metadata` to create Kubernetes YAML manifests.

### Default Output Behavior

If you do not specify `--output` and `--diagram`, the tool creates both files.
The tool uses the base name of the definition file for output file names.
For example, `input.json` produces `input.yaml` and `input.mmd`.

If you specify only `--diagram`, the tool creates only the Mermaid diagram.
In this case, the tool does not require `--properties` and `--metadata`.

### Examples

Create both Kubernetes manifests and a Mermaid diagram:

```bash
stream-deployer -d stream.json -p stream.properties -m metadata.properties
```

Create only a Mermaid diagram:

```bash
stream-deployer -d stream.json --diagram=topology.mmd
```

Create Kubernetes manifests with an explicit output file:

```bash
stream-deployer -d stream.json -p stream.properties -m metadata.properties -o manifests.yaml
```

## Building Distributions

The project supports both JVM distributions and GraalVM native distributions.

### Build JVM Distribution

Run the following command to build the JVM distribution archives:

```bash
./gradlew assembleDist
```

Gradle creates the following distribution files in `build/distributions/`:
- `stream-deployer-1.0.0-SNAPSHOT.zip`
- `stream-deployer-1.0.0-SNAPSHOT.tar`

To prepare an unpacked distribution directory, run:

```bash
./gradlew installDist
```

Gradle creates the unpacked distribution in `build/install/stream-deployer/`.
The directory contains:
- `bin/stream-deployer` (Unix launch script)
- `bin/stream-deployer.bat` (Windows launch script)
- `lib/` (Application and dependency JAR files)

### Build Native Distribution

Compile a standalone native binary with GraalVM:

```bash
./gradlew nativeCompile
```

Gradle compiles the binary into `build/native/nativeCompile/`.
The output files include:
- `stream-deployer` (Executable binary on Linux and macOS)
- `stream-deployer.exe` (Executable binary on Windows)
- Shared library files (`.so` or `.dll`), if present

## Installing on Linux and macOS

You can install either the JVM distribution or the native binary.

### Install JVM Build on Linux and macOS

1. Run the Gradle `installDist` task:
   ```bash
   ./gradlew installDist
   ```
2. Create the destination directories:
   ```bash
   mkdir -p "$HOME/.local/bin" "$HOME/.local/lib"
   ```
3. Copy the launcher script to `$HOME/.local/bin`:
   ```bash
   cp build/install/stream-deployer/bin/stream-deployer "$HOME/.local/bin/"
   ```
4. Copy the libraries to `$HOME/.local/lib`:
   ```bash
   cp -r build/install/stream-deployer/lib "$HOME/.local/"
   ```
5. Ensure `$HOME/.local/bin` is in your `PATH` variable:
   ```bash
   export PATH="$HOME/.local/bin:$PATH"
   ```
6. Verify the installation:
   ```bash
   stream-deployer --help
   ```

You can also copy the distribution folder and link the binary:
```bash
cp -r build/install/stream-deployer "$HOME/.local/"
ln -sf "$HOME/.local/stream-deployer/bin/stream-deployer" "$HOME/.local/bin/stream-deployer"
```

### Install Native Build on Linux and macOS

1. Compile the native image:
   ```bash
   ./gradlew nativeCompile
   ```
2. Create the destination directory:
   ```bash
   mkdir -p "$HOME/.local/bin"
   ```
3. Copy the executable binary to `$HOME/.local/bin`:
   ```bash
   cp build/native/nativeCompile/stream-deployer "$HOME/.local/bin/"
   ```
4. Copy any shared library files (`.so` files) if present:
   ```bash
   cp build/native/nativeCompile/*.so "$HOME/.local/bin/" 2>/dev/null || true
   ```
5. Set execute permissions on the binary:
   ```bash
   chmod +x "$HOME/.local/bin/stream-deployer"
   ```
6. Ensure `$HOME/.local/bin` is in your `PATH` variable.
7. Verify the installation:
   ```bash
   stream-deployer --help
   ```

## Installing on Windows

You can install either the JVM distribution or the native binary on Windows.

### Install JVM Build on Windows

1. Run the Gradle `installDist` task in Command Prompt or PowerShell:
   ```cmd
   gradlew.bat installDist
   ```
2. Copy the unpacked directory to your chosen installation path:
   ```cmd
   xcopy /E /I build\install\stream-deployer "%USERPROFILE%\.local\stream-deployer"
   ```
3. Add the `bin` directory to your `PATH` environment variable:
   ```cmd
   setx PATH "%USERPROFILE%\.local\stream-deployer\bin;%PATH%"
   ```
4. Open a new terminal window to refresh environment variables.
5. Verify the installation:
   ```cmd
   stream-deployer.bat --help
   ```

### Install Native Build on Windows

1. Compile the native image in Command Prompt or PowerShell:
   ```cmd
   gradlew.bat nativeCompile
   ```
2. Create your local bin directory if it does not exist:
   ```cmd
   if not exist "%USERPROFILE%\.local\bin" mkdir "%USERPROFILE%\.local\bin"
   ```
3. Copy the executable and any DLL files to your local bin directory:
   ```cmd
   copy build\native\nativeCompile\stream-deployer.exe "%USERPROFILE%\.local\bin\"
   copy build\native\nativeCompile\*.dll "%USERPROFILE%\.local\bin\" 2>nul
   ```
4. Add `%USERPROFILE%\.local\bin` to your `PATH` environment variable if necessary:
   ```cmd
   setx PATH "%USERPROFILE%\.local\bin;%PATH%"
   ```
5. Open a new terminal window.
6. Verify the installation:
   ```cmd
   stream-deployer --help
   ```
