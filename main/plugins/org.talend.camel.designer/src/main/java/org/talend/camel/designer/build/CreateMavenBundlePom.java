// ============================================================================
//
// Copyright (C) 2006-2018 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.camel.designer.build;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.talend.commons.exception.ExceptionHandler;
import org.talend.core.model.process.JobInfo;
import org.talend.core.model.properties.Property;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.core.runtime.process.LastGenerationInfo;
import org.talend.core.runtime.projectsetting.IProjectSettingPreferenceConstants;
import org.talend.core.runtime.projectsetting.IProjectSettingTemplateConstants;
import org.talend.designer.maven.model.MavenSystemFolders;
import org.talend.designer.maven.model.TalendMavenConstants;
import org.talend.designer.maven.template.MavenTemplateManager;
import org.talend.designer.maven.tools.creator.CreateMavenJobPom;
import org.talend.designer.maven.utils.PomIdsHelper;
import org.talend.designer.maven.utils.PomUtil;
import org.talend.designer.runprocess.IProcessor;
import org.talend.utils.io.FilesUtils;

public class CreateMavenBundlePom extends CreateMavenJobPom {

    private Model model;

    /**
     * DOC sunchaoqun CreateMavenCamelPom constructor comment.
     *
     * @param jobProcessor
     * @param pomFile
     */
    public CreateMavenBundlePom(IProcessor jobProcessor, IFile pomFile) {
        super(jobProcessor, pomFile);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.talend.designer.maven.tools.creator.CreateMavenBundleTemplatePom#create(org.eclipse.core.runtime.
     * IProgressMonitor)
     */
    @Override
    public void create(IProgressMonitor monitor) throws Exception {

        IFile curPomFile = getPomFile();

        if (curPomFile == null) {
            return;
        }

        model = createModel();

        IFolder resFolder = curPomFile.getParent().getProject().getFolder(MavenSystemFolders.RESOURCES.getPath());

        IContainer parent = curPomFile.getParent();

        Model pom = new Model();

        boolean route = "CAMEL".equals(getJobProcessor().getProcess().getComponentsType());

        if (route) {

            File featurePom = new File(parent.getLocation().toOSString() + File.separator + "pom-feature.xml");

            Model fm = new Model();

            fm.setModelVersion("4.0.0");
            fm.setGroupId(model.getGroupId());
            fm.setArtifactId(model.getArtifactId() + "-Feature");

            fm.setName(model.getName() + " Feature");

            fm.setVersion(model.getVersion());
            fm.setPackaging("pom");
            Build fmBuild = new Build();
            fmBuild.addPlugin(addFeaturesMavenPlugin(model.getProperties().getProperty("talend.job.finalName")));

            Set<JobInfo> subjobs = getJobProcessor().getBuildChildrenJobs();
            if (subjobs != null && !subjobs.isEmpty()) {
                for (JobInfo subjob : subjobs) {
                    if (isRoutelet(subjob)) {
                        fmBuild.addPlugin(addFileInstallPlugin(subjob));
                    }
                }
            }

            fm.setBuild(fmBuild);
            /*
             * <modelVersion>4.0.0</modelVersion>
             * 
             * <groupId>org.talend.job.ffffff</groupId> <artifactId>simpleRoute-feature</artifactId>
             * <version>3.0.0</version> <packaging>pom</packaging> <build> <plugins> <plugin>
             * <groupId>org.apache.karaf.tooling</groupId> <artifactId>features-maven-plugin</artifactId>
             * <version>2.2.9</version> <executions> <execution> <id>create-kar</id> <goals> <goal>create-kar</goal>
             * </goals> <configuration> <finalName>simpleroute_3_0</finalName>
             * <resourcesDir>${project.build.directory}/bin</resourcesDir>
             * <featuresFile>/Volumes/M2/tmp/feature/feature.xml</featuresFile> </configuration> </execution>
             * </executions> </plugin> </plugins> </build>
             * 
             */

            PomUtil.savePom(monitor, fm, featurePom);

        }

        pom.setModelVersion("4.0.0");
        // pom.setParent(model.getParent());
        pom.setGroupId(model.getGroupId());
        pom.setArtifactId(model.getArtifactId() + "-Kar");
        pom.setName(model.getName() + " Kar");
        pom.setVersion(model.getVersion());
        pom.setPackaging("pom");

        for (JobInfo job : getJobProcessor().getBuildChildrenJobs()) {
            if (isRoutelet(job)) {
            	IPath currentProjectRootDir = getJobProcessor().getTalendJavaProject().getProject().getLocation();
            	IPath routeletPomPath = job.getProcessor().getTalendJavaProject().getProjectPom().getLocation();
            	String relativePomPath = routeletPomPath.makeRelativeTo(currentProjectRootDir).toString();
                pom.addModule(relativePomPath);
            }
        }

        pom.addModule("pom-bundle.xml");
        if (route) {
            pom.addModule("pom-feature.xml");
        } else {
            for (JobInfo job : LastGenerationInfo.getInstance().getLastGeneratedjobs()) {
                if (model.getArtifactId().equals(job.getJobName())) {
                    if (job.getFatherJobInfo() != null) {
                        model.setArtifactId(job.getFatherJobInfo().getJobName() + "_" + model.getArtifactId());
                        break;
                    }
                }
            }

        }
        pom.setDependencies(model.getDependencies());

        /*
         * 
         * <modelVersion>4.0.0</modelVersion> <parent> <groupId>org.talend.master.ffffff</groupId>
         * <artifactId>code.Master</artifactId> <version>7.0.1</version> <relativePath>../../../</relativePath>
         * </parent> <groupId>org.talend.job.ffffff</groupId> <artifactId>simpleRoute-ogsi</artifactId>
         * <version>3.0.0</version> <packaging>pom</packaging> <modules> <module>a.xml</module> <module>b.xml</module>
         * </modules>
         * 
         */

        File bd = new File(parent.getLocation().toOSString() + File.separator + "pom-bundle.xml");
        // model.setParent(null);
        // model.setDependencies(null);

        // List<Plugin> plugins = model.getBuild().getPlugins();
        //
        // for (Plugin plugin : plugins) {
        // if (plugin.getArtifactId().equals("maven-jar-plugin")) {
        // PluginExecution pluginExecution = plugin.getExecutionsAsMap().get("default-jar");
        // Xpp3Dom configuration = (Xpp3Dom) pluginExecution.getConfiguration();
        // /*
        // * <archive> <manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile>
        // * </archive>
        // */
        //
        // Xpp3Dom archive = new Xpp3Dom("archive");
        // Xpp3Dom manifestFile = new Xpp3Dom("manifestFile");
        // manifestFile.setValue("${project.build.outputDirectory}/META-INF/MANIFEST.MF");
        //
        // archive.addChild(manifestFile);
        //
        // configuration.addChild(archive);
        // System.out.println(configuration);
        // }
        // }

        List<Profile> profiles = model.getProfiles();

        for (Profile profile : profiles) {

            if (profile.getId().equals("packaging-and-assembly")) {
                List<Plugin> plugins = profile.getBuild().getPlugins();

                for (Plugin plugin : plugins) {
                    if (plugin.getArtifactId().equals("maven-assembly-plugin")) {
                        PluginExecution pluginExecution = plugin.getExecutionsAsMap().get("default");
                        Xpp3Dom configuration = (Xpp3Dom) pluginExecution.getConfiguration();
                        /*
                         * <archive> <manifestFile>${project.build.outputDirectory}/META-INF/MANIFEST.MF</manifestFile>
                         * </archive>
                         */

                        Xpp3Dom archive = new Xpp3Dom("archive");
                        Xpp3Dom manifestFile = new Xpp3Dom("manifestFile");
                        manifestFile.setValue("${current.bundle.resources.dir}/META-INF/MANIFEST.MF");

                        archive.addChild(manifestFile);

                        configuration.addChild(archive);
                        // System.out.println(configuration);
                    }
                }

            }

        }

        model.setName(model.getName() + " Bundle");

        PomUtil.savePom(monitor, model, bd);

        PomUtil.savePom(monitor, pom, curPomFile);

        parent.refreshLocal(IResource.DEPTH_ONE, monitor);

        afterCreate(monitor);

    }

    protected void generateAssemblyFile(IProgressMonitor monitor, final Set<JobInfo> clonedChildrenJobInfors) throws Exception {
        IFile assemblyFile = this.getAssemblyFile();
        if (assemblyFile != null) {
            boolean set = false;
            // read template from project setting
            try {
                File templateFile = PomUtil.getTemplateFile(getObjectTypeFolder(), getItemRelativePath(),
                        TalendMavenConstants.ASSEMBLY_FILE_NAME);
                if (!FilesUtils.allInSameFolder(templateFile, TalendMavenConstants.POM_FILE_NAME)) {
                    templateFile = null; // force to set null, in order to use the template from other places.
                }

                final Map<String, Object> templateParameters = PomUtil.getTemplateParameters(getJobProcessor());
                String content = MavenTemplateManager.getTemplateContent(templateFile,
                        IProjectSettingPreferenceConstants.TEMPLATE_ROUTE_ASSEMBLY, JOB_TEMPLATE_BUNDLE,
                        IProjectSettingTemplateConstants.PATH_OSGI_BUNDLE + '/'
                                + IProjectSettingTemplateConstants.ASSEMBLY_ROUTE_TEMPLATE_FILE_NAME,
                        templateParameters);
                if (content != null) {
                    ByteArrayInputStream source = new ByteArrayInputStream(content.getBytes());
                    if (assemblyFile.exists()) {
                        assemblyFile.setContents(source, true, false, monitor);
                    } else {
                        assemblyFile.create(source, true, monitor);
                    }
                    updateDependencySet(assemblyFile);
                    set = true;
                }
            } catch (Exception e) {
                ExceptionHandler.process(e);
            }
        }
    }

    private Plugin addMavenBundlePlugin() {

        /*
         * <plugin> <groupId>org.apache.felix</groupId> <artifactId>maven-bundle-plugin</artifactId>
         * <version>3.3.0</version> <extensions>true</extensions> <configuration> <archive>
         * <addMavenDescriptor>false</addMavenDescriptor> </archive> <instructions>
         * <Bundle-SymbolicName>${project.groupId}.${project.artifactId}</Bundle-SymbolicName>
         * <Bundle-Name>${talend.job.name}</Bundle-Name> <Bundle-Version>${project.version}</Bundle-Version>
         * <Export-Package>${bundle.config.export.package}</Export-Package>
         * <Export-Service>${bundle.config.export.service}</Export-Service>
         * <Import-Package>${bundle.config.import.package}, *;resolution:=optional</Import-Package>
         * <Include-Resource>{maven-resources}, {maven-dependencies},</Include-Resource> <Bundle-ClassPath>.,
         * {maven-dependencies}</Bundle-ClassPath> </instructions> </configuration> </plugin>
         */

        Plugin plugin = new Plugin();

        plugin.setGroupId("org.apache.felix");
        plugin.setArtifactId("maven-bundle-plugin");
        plugin.setVersion("3.3.0");

        plugin.setExtensions(true);

        Xpp3Dom configuration = new Xpp3Dom("configuration");

        Xpp3Dom archive = new Xpp3Dom("archive");
        Xpp3Dom addMavenDescriptor = new Xpp3Dom("addMavenDescriptor");

        addMavenDescriptor.setValue("false");
        archive.addChild(addMavenDescriptor);

        Xpp3Dom instructions = new Xpp3Dom("instructions");

        Xpp3Dom bundleSymbolicName = new Xpp3Dom("Bundle-SymbolicName");
        bundleSymbolicName.setValue("${project.groupId}.${project.artifactId}");

        Xpp3Dom bundleName = new Xpp3Dom("Bundle-Name");
        bundleName.setValue("${talend.job.name}");

        Xpp3Dom bundleVersion = new Xpp3Dom("Bundle-Version");
        bundleVersion.setValue("${project.version}");

        Xpp3Dom exportPackage = new Xpp3Dom("Export-Package");
        exportPackage.setValue("${bundle.config.export.package}");

        Xpp3Dom exportService = new Xpp3Dom("Export-Service");
        exportService.setValue("${bundle.config.export.service}");

        Xpp3Dom importPackage = new Xpp3Dom("Import-Package");
        importPackage.setValue("${bundle.config.import.package}, *;resolution:=optional");

        Xpp3Dom bundleClassPath = new Xpp3Dom("Bundle-ClassPath");
        bundleClassPath.setValue("., {maven-dependencies}");

        instructions.addChild(bundleSymbolicName);
        instructions.addChild(bundleName);
        instructions.addChild(bundleVersion);
        instructions.addChild(exportPackage);
        instructions.addChild(exportService);
        instructions.addChild(importPackage);
        instructions.addChild(bundleClassPath);

        configuration.addChild(instructions);

        plugin.setConfiguration(configuration);

        return plugin;
    }

    private Plugin addFeaturesMavenPlugin(String finalNameValue) {
        Plugin plugin = new Plugin();

        plugin.setGroupId("org.apache.karaf.tooling");
        plugin.setArtifactId("features-maven-plugin");
        plugin.setVersion("2.2.9");

        Xpp3Dom configuration = new Xpp3Dom("configuration");

        Xpp3Dom finalName = new Xpp3Dom("finalName");

        finalName.setValue(finalNameValue);// "${talend.job.finalName}"

        Xpp3Dom resourcesDir = new Xpp3Dom("resourcesDir");
        resourcesDir.setValue("${project.build.directory}/bin");

        Xpp3Dom featuresFile = new Xpp3Dom("featuresFile");
        featuresFile.setValue("${basedir}/src/main/bundle-resources/feature.xml");

        configuration.addChild(finalName);
        configuration.addChild(resourcesDir);
        configuration.addChild(featuresFile);

        List<PluginExecution> pluginExecutions = new ArrayList<PluginExecution>();
        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.setId("create-kar");
        pluginExecution.addGoal("create-kar");
        pluginExecution.setConfiguration(configuration);

        pluginExecutions.add(pluginExecution);
        plugin.setExecutions(pluginExecutions);

        return plugin;
    }

    private Plugin addFileInstallPlugin(JobInfo routlet) {
        Plugin plugin = new Plugin();

        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-install-plugin");
        plugin.setVersion("2.5.1");
        
        String jobVersion = PomIdsHelper.getJobVersion(routlet);

        Xpp3Dom configuration = new Xpp3Dom("configuration");

        Xpp3Dom groupId = new Xpp3Dom("groupId");
        groupId.setValue(model.getGroupId());

        Xpp3Dom artifactId = new Xpp3Dom("artifactId");
        artifactId.setValue(model.getArtifactId() + "_" + routlet.getJobName());

        Xpp3Dom version = new Xpp3Dom("version");
        version.setValue(PomIdsHelper.getJobVersion(routlet.getProcessItem().getProperty()));

        Xpp3Dom packaging = new Xpp3Dom("packaging");
        packaging.setValue("jar");

        Xpp3Dom file = new Xpp3Dom("file");
        IPath currentProjectRootDir = getJobProcessor().getTalendJavaProject().getProject().getLocation();
        IPath routeletTargetDir = routlet.getProcessor().getTalendJavaProject().getTargetFolder().getLocation();
        String relativeRouteletTargetDir = routeletTargetDir.makeRelativeTo(currentProjectRootDir).toString();
        String pathToJar = relativeRouteletTargetDir + Path.SEPARATOR + routlet.getJobName().toLowerCase() + "_"
                + jobVersion.replaceAll("\\.", "_") + ".jar";
        file.setValue(pathToJar);

        Xpp3Dom generatePom = new Xpp3Dom("generatePom");
        generatePom.setValue("true");

        configuration.addChild(groupId);
        configuration.addChild(artifactId);
        configuration.addChild(version);
        configuration.addChild(packaging);
        configuration.addChild(file);
        configuration.addChild(generatePom);

        List<PluginExecution> pluginExecutions = new ArrayList<PluginExecution>();
        PluginExecution pluginExecution = new PluginExecution();
        pluginExecution.setId("install-jar-lib");
        pluginExecution.addGoal("install-file");
        pluginExecution.setPhase("validate");

        pluginExecution.setConfiguration(configuration);
        pluginExecutions.add(pluginExecution);
        plugin.setExecutions(pluginExecutions);

        return plugin;
    }

    boolean isRoutelet(JobInfo job) {
        if (job != null && job.getProcessItem() != null) {
            Property p = job.getProcessItem().getProperty();
            if (p != null) {
                return ERepositoryObjectType.getType(p).equals(ERepositoryObjectType.PROCESS_ROUTELET);
            }
        }
        return false;
    }
}
