# Stream Deployer

Stream Deployer creates Kubernetes manifests and Mermaid diagrams from Spring Cloud Data Flow stream definitions.
It resolves stream bindings and injects communication channels and groups into container arguments.

The stream definition JSON:
```json
{
  "streams": [
    {
      "name": "time-logger",
      "dslText": "time | log",
      "originalDslText": "time | log",
      "description": "Generates timestamps and logs the timestamps"
    }
  ]
}
```

will produce a diagram like below.

```mermaid
---
title: time-logger - Generates timestamps and logs the timestamps
---
%%{init: {"theme": "dark", "themeCSS": ".label { color: white; font-weight: normal; display: inline-flex; align-items; center; justify-content: center; border-radius: 9999px; padding: 4px 12px; } .name { color: lightgrey; font-weight: normal; padding: 4px 12px; } .sink-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .sink-node .label { background-color: #f5be00; } .source-node { background-color: #17242b; text-align: left; justify-content: left; margin-right: 16px; } .source-node .label { background-color: #0096ff; } "}}%%
flowchart LR
    time["<div class='source-node'><span class='name'>time</span><br><span class='label'>TIME</span></div>"]:::source
    log["<div class='sink-node'><span class='name'>log</span><br><span class='label'>LOG</span></div>"]:::sink
    time --> log

    classDef source fill:#17242b,stroke:#0096ff,stroke-width:3px;
    classDef sink fill:#17242b,stroke:#f5be00,stroke-width:3px;
    class log sink;
    class time source;

    linkStyle default stroke-width:3px;

```

## Requirements

The project requires the following software:
- Java 25 (SDKMAN or compatible JDK)
- GraalVM with `native-image` (for native compilation and native testing)

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

You can compile a standalone native executable with GraalVM.
The build requires GraalVM for JDK 25 with the `native-image` component installed.

Run the following command to compile the native executable:

```bash
./gradlew nativeCompile
```

Gradle uses the following build configuration:
- Builds a standalone executable without fallback (`--no-fallback`).
- Emits a build report with compilation statistics.
- Uses GraalVM reachability metadata repository support.

Gradle writes the output files to `build/native/nativeCompile/`:
- `stream-deployer` (Executable binary on Linux and macOS)
- `stream-deployer.exe` (Executable binary on Windows)
- Shared library files (`.so`, `.dylib`, or `.dll`), if present

### Run Native Tests

You can compile and execute tests as a native image.
Native tests verify application behavior and reachability metadata in a native environment.

Run the following command to execute native tests:

```bash
./gradlew nativeTest
```

Gradle compiles test classes into a native executable and runs the tests.
The test results and reports are saved to `build/reports/tests/nativeTest/`.

### Generate Reachability Metadata

The project uses GraalVM reachability metadata to configure reflection, serialization, and resources.
Standard JVM tests run without the tracing agent to avoid test execution conflicts.

When you add new dynamic dependencies or reflection code, collect updated metadata.

1. Run the test suite with the native agent enabled:

   ```bash
   ./gradlew -Pagent test
   ```

   The agent records dynamic access and writes configuration files to `build/native/agent-output/test/`.

2. Copy the generated metadata into the project source tree:

   ```bash
   ./gradlew metadataCopy --task test --dir src/main/resources/META-INF/native-image
   ```

3. Review the updated JSON files in `src/main/resources/META-INF/native-image/` and commit them to version control.

## Installing Locally

You can install either the JVM distribution or the native binary to your local user environment.

The project provides two Gradle tasks for local installation:
- `installLocalJvm`: Builds the JVM distribution and copies launcher scripts and libraries.
- `installLocalNative`: Compiles the GraalVM native binary and copies executable files and shared libraries.

### Default Installation Paths

The installation tasks use the following default paths:

- **Linux and macOS**:
  - Binaries and scripts: `$HOME/.local/bin`
  - JVM libraries: `$HOME/.local/lib`
- **Windows**:
  - Binaries and scripts: `%LOCALAPPDATA%\Programs\stream-deployer\bin`
  - JVM libraries: `%LOCALAPPDATA%\Programs\stream-deployer\lib`

You can change the target installation directory with the `-PinstallDir=<path>` option:

```bash
./gradlew installLocalJvm -PinstallDir="$HOME/custom-tools"
```

## Installing on Linux and macOS

### Install JVM Build on Linux and macOS

1. Run the `installLocalJvm` task:
   ```bash
   ./gradlew installLocalJvm
   ```
   This task runs `installDist`, creates `$HOME/.local/bin` and `$HOME/.local/lib` if necessary, and copies the application files.

2. Ensure `$HOME/.local/bin` is in your `PATH` environment variable:
   ```bash
   export PATH="$HOME/.local/bin:$PATH"
   ```

3. Verify the installation:
   ```bash
   stream-deployer --help
   ```

### Install Native Build on Linux and macOS

1. Run the `installLocalNative` task:
   ```bash
   ./gradlew installLocalNative
   ```
   This task runs `nativeCompile`, creates `$HOME/.local/bin` if necessary, copies `stream-deployer` and shared libraries (`.so`, `.dylib`), and sets executable permissions (`0755`).

2. Ensure `$HOME/.local/bin` is in your `PATH` environment variable.

3. Verify the installation:
   ```bash
   stream-deployer --help
   ```

## Installing on Windows

### Install JVM Build on Windows

1. Run the `installLocalJvm` task in Command Prompt or PowerShell:
   ```cmd
   gradlew.bat installLocalJvm
   ```
   This task runs `installDist`, creates `%LOCALAPPDATA%\Programs\stream-deployer\bin` and `lib` directories if necessary, and copies the files.

2. Add the binary directory to your `PATH` environment variable:
   ```cmd
   setx PATH "%LOCALAPPDATA%\Programs\stream-deployer\bin;%PATH%"
   ```

3. Open a new terminal window to refresh environment variables.

4. Verify the installation:
   ```cmd
   stream-deployer.bat --help
   ```

### Install Native Build on Windows

1. Run the `installLocalNative` task in Command Prompt or PowerShell:
   ```cmd
   gradlew.bat installLocalNative
   ```
   This task runs `nativeCompile`, creates `%LOCALAPPDATA%\Programs\stream-deployer\bin` if necessary, and copies `stream-deployer.exe` and any dynamic library files (`.dll`).

2. Add the binary directory to your `PATH` environment variable if necessary:
   ```cmd
   setx PATH "%LOCALAPPDATA%\Programs\stream-deployer\bin;%PATH%"
   ```

3. Open a new terminal window.

4. Verify the installation:
   ```cmd
   stream-deployer --help
   ```
