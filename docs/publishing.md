# Publishing Jaspring

The build publishes two Java 25 libraries and a Gradle plugin:

- `dev.isira:jaspring-core`
- `dev.isira:jaspring-spring-boot-starter`
- `dev.isira:jaspring-gradle-plugin` (plugin ID `dev.isira.jaspring`)

The plugin also publishes its marker POM,
`dev.isira.jaspring:dev.isira.jaspring.gradle.plugin`, so Gradle's `plugins` block
can resolve it from Maven Central. Consumers must add `mavenCentral()` under
`pluginManagement.repositories`; see [Gradle plugin setup](../jaspring-gradle-plugin/README.md).
Publishing to the Gradle Plugin Portal is not required and is not configured here.

The sample application and tools are not published. All three artifacts include the
binary, sources, Javadoc, POM and Gradle module metadata. The POMs include project,
developer, MIT license and SCM information. Jars include Jaspring's license;
bundled JavaScript retains its upstream license texts.

The default development version is `0.0.1-SNAPSHOT`. Pass `-PreleaseVersion=0.0.1`
to select a release version for all modules, including the starter's dependency on
core. Versions follow `major.minor.patch`, optionally with a prerelease suffix
such as `-rc.1`. `releaseVersion` rejects snapshots and malformed values.

## Local verification

Use Oracle GraalVM 25.2.4 and the committed Gradle wrapper: `jaspring-browser-runtime` compiles
against GraalVM's `org.graalvm.webimage.api` module (on another JDK 25 that module is skipped with a
warning and the rest still builds). Normal JVM builds use the committed JavaScript bundle and do not
need Node or pnpm; the client shim's tests (`tools/shim-tests`) run when Node 24 is installed and
`pnpm install` was run there. The sample's WebAssembly build needs Binaryen's `wasm-as` 119+ and runs
only with `-Pjaspring.wasm=true`.

```sh
./gradlew clean build publishAllPublicationsToBuildRepository
python3 tools/verify-publications.py
```

Tests run across all four modules. The repository under `build/repo` lets you
inspect the generated files without uploading anything or changing Maven Local.
The Python 3 verifier checks artifact coverage, metadata, dependency relationships,
checksums, sources, Javadoc and packaged resources. Without `--version`, it selects
the most recently published core version from the local repository. Snapshot verification needs no
publishing credentials or signing key. Javadoc currently reports missing-comment
warnings but generates full API documentation.

To install the development version for another local application:

```sh
./gradlew publishToMavenLocal
```

Use `dev.isira:jaspring-spring-boot-starter:0.0.1-SNAPSHOT` and add `mavenLocal()` to
the consuming Gradle build. Maven uses its local repository automatically.

## Container checks

On Linux x86-64 with Podman, Git and Python 3 installed:

```sh
bash tools/ci/run.sh
```

This runs the Build workflow's Gradle build, tests, local Maven publications and
Python checks in Ubuntu 24.04 with Oracle GraalVM 25.2.4, Binaryen and Node. It copies tracked
working-tree files, including edits, into `build/ci-podman.*/workspace`; stage new
source files before running to include them. Test reports and local artifacts stay
in that snapshot. A dedicated Podman volume, `jaspring-ci-repro-gradle`, caches
Gradle downloads. Host Gradle configuration and publishing credentials are not
mounted, and nothing is uploaded to Central.

To run just the GraalJS runtime test:

```sh
bash tools/ci/run.sh :jaspring-core:test --tests dev.isira.jaspring.GraalJsSmokeTest
```

The container matches the build OS, JDK and commands, but does not emulate the
GitHub Actions service, runner kernel or action wrappers. Set `GRAALVM_URL` to
another Linux x64 GraalVM archive to compare runtimes.

Keep the GraalVM version in both workflows and the Containerfile aligned with
`graal` in `gradle/libs.versions.toml`. Java's version alone is insufficient:
GraalVM 25.0.4 and 25.2.4 both use JDK 25.0.4, but only the latter provides a
compiler compatible with this project's Polyglot 25.2.4. The former falls back to
interpreted execution and fails the optimizing-runtime smoke test.

## Central Portal setup

1. Create a [Central Portal account](https://central.sonatype.org/register/central-portal/)
   and [verify the `dev.isira` namespace](https://central.sonatype.org/register/namespace/).
   This requires control of `isira.dev`; the GitHub repository alone does not grant
   publishing access to this namespace.
2. Generate a [Portal user token](https://central.sonatype.org/publish/generate-portal-token/).
   Publishing uses the token's username and password, not your website login.
3. Create a signing key and [distribute its public key](https://central.sonatype.org/publish/requirements/gpg/)
   as required by Central. Keep the private key and passphrase outside this repository.

The build uses the [Vanniktech Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
with the Central Portal API. It generates checksums and signs publications to meet
[Central's artifact requirements](https://central.sonatype.org/publish/requirements/).

Provide these Gradle properties in `~/.gradle/gradle.properties` or through the
corresponding environment variables:

| Environment variable                            | Value                                                                             |
|-------------------------------------------------|-----------------------------------------------------------------------------------|
| `ORG_GRADLE_PROJECT_mavenCentralUsername`       | Portal token username                                                             |
| `ORG_GRADLE_PROJECT_mavenCentralPassword`       | Portal token password                                                             |
| `ORG_GRADLE_PROJECT_signingInMemoryKey`         | Complete ASCII-armored private signing key, including header, footer and newlines |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | Signing key passphrase, if set                                                    |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyId`       | Optional key ID, needed when selecting a signing subkey                           |

For property files, omit the `ORG_GRADLE_PROJECT_` prefix; represent newlines in the
armored key with `\n`. Prefer environment variables or a secret store for multiline
keys. Do not place credentials in project files or command-line arguments.

## Stage a release

Choose an unused version, update the installation examples, and commit the resulting
README changes with the release preparation:

```sh
./gradlew updateReadmeVersions -PreleaseVersion=0.3.0
```

This updates Jaspring dependency coordinates, the Maven dependency version and the
Gradle plugin version in the main and Gradle-plugin READMEs. Other dependencies and
historical example commands are left alone. Python 3 is required, as for the
publication verifier. The task rejects snapshots and is safe to run repeatedly.

Release invocations of `publishToMavenCentral` (and `publishAndReleaseToMavenCentral`)
also run this task automatically. Local snapshot publications and normal builds do
not change the documentation. Commit any resulting README changes; a local task
does not create Git commits. The CI release workflow runs `checkReadmeVersions`
before building or uploading so an uncommitted update cannot disappear with the
temporary runner checkout.

Then publish from the reviewed commit. For example:

```sh
export VERSION=<version>
./gradlew clean build -PreleaseVersion=$VERSION
./gradlew publishAllPublicationsToBuildRepository -PreleaseVersion=$VERSION
python3 tools/verify-publications.py --version $VERSION --require-signatures
./gradlew publishToMavenCentral -PreleaseVersion=$VERSION
```

The first command needs no signing credentials; the second needs the signing key;
the upload also needs the Portal token. The verifier checks that signature files
exist and are armored; Central verifies their validity and public-key availability.

`publishToMavenCentral` uploads the libraries, Gradle plugin and marker into a deployment for review. Open
[Central Portal deployments](https://central.sonatype.com/publishing/deployments),
wait for successful validation, inspect the artifacts, and click **Publish**.
Published versions cannot be replaced. Tag the released commit with its version,
then advance the default snapshot version in `build.gradle.kts` for the next
development cycle.

Do not omit `releaseVersion` when releasing: the default snapshot is routed to
Central's separate snapshot repository, which must be enabled for the namespace.

## GitHub Actions

The Build workflow tests and verifies local snapshot publications on pushes and
pull requests, without publishing secrets. The **Stage Maven Central release**
workflow is manually dispatched with a release version from the selected branch
or tag. It checks committed README versions, tests the release, verifies signed
local artifacts, then uploads them for review in Central Portal. It does not
automatically release the deployment.

Configure these repository Actions secrets before running it:

| Secret                   | Value                              |
|--------------------------|------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | Portal token username              |
| `MAVEN_CENTRAL_PASSWORD` | Portal token password              |
| `MAVEN_SIGNING_KEY`      | Complete ASCII-armored private key |
| `MAVEN_SIGNING_PASSWORD` | Key passphrase, if set             |

The workflow grants only read access to repository contents and serializes release
uploads. Namespace verification, key registration and secret configuration are
account setup steps; committing this build configuration does not perform them.
