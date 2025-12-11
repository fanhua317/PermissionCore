# Code generation helper

This project includes a Maven `codegen` profile to run the code generator using an in-memory H2 database.

Quick steps (PowerShell):

1. Open PowerShell and cd to project root (where `pom.xml` is):

```powershell
cd "C:\Users\Administrator\IdeaProjects\Permission Core"
```

2. (Optional) Ensure JDK available in session (replace path with your JDK):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot'
$env:PATH = $env:JAVA_HOME + '\\bin;' + $env:PATH
java -version
javac -version
```

3. Run code generator (one command):

```powershell
mvn -Pcodegen -DskipTests generate-sources
```

The profile runs the `exec-maven-plugin` in the `generate-sources` phase and sets `useH2=true` so the generator will use an in-memory H2 DB and create minimal tables automatically.

4. After it finishes, refresh the project and check `src/main/java/com/permacore/iam/domain/entity`, `mapper`, `service`, and `src/main/resources/mapper` for generated files.

If the run fails, paste the full Maven output here and I'll diagnose further.
