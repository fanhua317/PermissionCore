# Code Generation Helper

The code generator lives in the independent `codegen` Maven module. The root project does not provide a `-Pcodegen` profile.

PowerShell from the project root:

```powershell
cd "D:\学习\RBAC\PermissionCore"
mvn -f codegen/pom.xml -DuseH2=true -DskipTests org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

Equivalent flow:

```powershell
cd "D:\学习\RBAC\PermissionCore\codegen"
mvn -DuseH2=true -DskipTests org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

`useH2=true` makes the generator use an in-memory H2 database and create the minimal metadata tables it needs.
