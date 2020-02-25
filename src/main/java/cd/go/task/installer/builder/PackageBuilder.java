/*
 * Copyright (c) 2001-2019 Territorium Online Srl / TOL GmbH. All Rights Reserved.
 *
 * This file contains Original Code and/or Modifications of Original Code as defined in and that are
 * subject to the Territorium Online License Version 1.0. You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at http://www.tol.info/license/
 * and read it before using this file.
 *
 * The Original Code and all software distributed under the License are distributed on an 'AS IS'
 * basis, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED, AND TERRITORIUM ONLINE HEREBY
 * DISCLAIMS ALL SUCH WARRANTIES, INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT. Please see the License for
 * the specific language governing rights and limitations under the License.
 */

package cd.go.task.installer.builder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cd.go.task.installer.Packages;

/**
 * The {@link PackageBuilder} is an utility class that creates the package structure for the
 * installer. The builder copies the meta data from a source directory and creates a build
 * directory.
 * 
 * <pre>
 * -packages - com.vendor.root - data - meta - com.vendor.root.component1 - data - meta -
 * com.vendor.root.component1.subcomponent1 - data - meta - com.vendor.root.component2 - data - meta
 * 
 * <pre>
 */
public class PackageBuilder {

  private final File                workingDir;
  private final Map<String, String> environment;


  private String                  packagePath;
  private final List<PackageData> data = new ArrayList<>();

  /**
   * Constructs an instance of {@link PackageBuilder} for the working directory.
   *
   * @param workingDir
   * @param environment
   */
  private PackageBuilder(File workingDir, Map<String, String> environment) {
    this.workingDir = workingDir;
    this.environment = environment;
    this.environment.put(Packages.RELEASE_DATE, LocalDate.now().toString());
  }

  /**
   * Set the path to the package definitions.
   *
   * @param packagePath
   */
  public final void setPackagePath(String packagePath) {
    this.packagePath = packagePath;
  }

  /**
   * Add a package with the location of the package data.
   *
   * @param name
   * @param source
   * @param target
   */
  public void addPackage(String name, File source, String target) {
    this.data.add(new PackageData(name, source, target));
  }

  /**
   * Get the source path of the package definition.
   */
  protected final Path getSourcePath() {
    return workingDir.toPath().resolve(packagePath == null ? "packages" : packagePath);
  }

  /**
   * Get the target path for the build package structure.
   */
  protected final Path getTargetPath() {
    return workingDir.toPath().resolve(Packages.BUILD).resolve(Packages.BUILD_PKG);
  }

  /**
   * Copy all package definitions of the module and its dependencies.
   *
   * @param name
   */
  private void buildDependencies(String name) throws IOException {
    Map<String, String> modules = new HashMap<>();

    // Collect depending packages
    for (File file : getSourcePath().toFile().listFiles()) {
      String pkgName = Parameter.of(environment).replace(file.getName());
      File location = new File(getTargetPath().toFile(), pkgName);
      if (name.contains(pkgName) && !location.exists()) {
        modules.put(file.getName(), pkgName);
      }
    }

    // Process defined packages
    for (Map.Entry<String, String> module : modules.entrySet()) {
      Path sourcePath = getSourcePath().resolve(module.getKey());
      Path targetPath = getTargetPath().resolve(module.getValue());

      targetPath.toFile().mkdirs();
      FileTreeCopying.copyFileTree(sourcePath, targetPath, environment);
      File meta = new File(targetPath.toFile(), Packages.META);
      for (File file : meta.listFiles()) {
        String data = new String(Files.readAllBytes(file.toPath()));
        try (Writer writer = new FileWriter(file)) {
          writer.write(Parameter.of(environment).replace(data));
        }
      }
    }
  }

  /**
   * Build a package structure for the {@link PackagesBuilder}. The method expects a source
   * file/folder, the {@link Version} information and the relative installation path.
   *
   * Update the meta/package.xml with the actual version and release date.
   *
   * @param source
   * @param version
   * @param relativePath
   */
  public final void build() throws Exception {
    for (PackageData data : this.data) {
      buildDependencies(data.getName());
    }

    for (PackageData data : this.data) {
      data.build(getTargetPath().toFile(), environment);
    }
  }

  /**
   * Constructs an instance of {@link PackagesBuilder}, providing the working directory and the
   * environment variables.
   *
   * @param workingDir
   * @param environment
   */
  public static PackageBuilder of(File workingDir, Map<String, String> environment) {
    return new PackageBuilder(workingDir, environment);
  }
}
