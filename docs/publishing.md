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

Use JDK 25, preferably GraalVM, and the committed Gradle wrapper. Normal JVM builds
use the committed JavaScript bundle and do not need Node or pnpm.

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

Choose an unused version and a reviewed commit. For example:

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
or tag. It tests the release, verifies signed local artifacts, then uploads them
for review in Central Portal. It does not automatically release the deployment.

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
