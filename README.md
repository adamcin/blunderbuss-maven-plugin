Blunderbuss Maven Plugin
========================

[![Build Status](https://travis-ci.org/adamcin/blunderbuss-maven-plugin.png)](https://travis-ci.org/adamcin/blunderbuss-maven-plugin)
[![Coverage Status](https://coveralls.io/repos/github/adamcin/blunderbuss-maven-plugin/badge.svg?branch=master)](https://coveralls.io/github/adamcin/blunderbuss-maven-plugin?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/net.adamcin/blunderbuss-maven-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22net.adamcin%22%20AND%20a:%22blunderbuss-maven-plugin%22)

## About Blunderbuss

With the proliferation of low-feature cheap maven artifact repositories for cloud vendors that only support proxy to maven-central and lack advanced 
repo index services, we need a way to reliably maintain mirrors using an imperative incremental method like rsync. 

This plugin is not intended to completely replace full-featured reverse proxy maven artifact repositories like Nexus or Artifactory, or 
cloud artifact repositories with premium upstream proxy features like JFrog. Instead, it's intended to avoid having to scale up an off-the-shelf OSS 
Nexus repository or to pay more for a third-party cloud repository to meet the scalability of hosted maven CI pipelines executed by Azure Pipelines,
AWS CodeCommit, and GitHub Actions, by incrementally syncing all the resolved build dependencies to whatever feature-limited cloud artifact publishing 
product is most conveniently available, such as Azure Artifact Feeds, AWS CodeArtifact, or GitHub Packages.

What are the limitations that blunderbuss solves?

* *A cloud-hosted maven artifact store can't use a third-party repository (other than Maven Central) as an upstream proxy target.* Blunderbuss is a 
client-side artifact sync tool, so if you can fully build a project once in some context with access to all the third-party upstream repositories, 
so that all implicit, transitive, and profile-sensitive dependencies are resolved to a local maven repository folder, you can subsequently run the 
Blunderbuss `sync` goal to publish all those resolved dependencies to convenient store.

* *A cloud-hosted maven artifact store won't provide standard index metadata for all present artifacts.* Nexus and Maven Central have standardized 
repository-wide index APIs in one form or another that might support incremental diff calculation, but the cloud-hosted repository options don't 
have support for those index APIs, so artifact discovery from index metadata or by traversal of the default maven layout by scraping directory 
index.html files is not possible, with the only alternative being to glue something together using a proprietary SDK or CLI. Blunderbuss maintains 
its own simple index format in a `jar` package that follows the most basic Maven artifact rules.

* *Generic `curl`-based and `mvn deploy`-based solutions are not optimized for an UPSERT approach that retains native handling of Maven Metadata.* 
`mvn deploy` doesn't like UPSERTS at all, and implementing all the necessary XML merging steps for updating `maven-metadata.xml` file on upload in a 
`curl` glue script would be a maintenance nightmare as well. Blunderbuss uses native Maven repository APIs as much as possible to remain a useful 
and portable tool in the long-term, while deviating in the right places to implement the UPSERT behavior needed for an efficient rsync-like approach. 

This tool is best used when you can guarantee your local maven repository is as clean as you want the target artifact repo to be. This is not 
generally the situation for most developers running maven on their personal machines, who use the same maven cache for all projects without regularly
cleaning it. For use on personal machines, it is important to start with a clean local cache when preparing to use Blunderbuss to avoid leaking 
licensed/proprietary artifacts to the remote repository. In a CI build agent environment, on the other hand, the local maven cache is usually 
discarded after every build, so this is less of risk.

## Usage

### Pure CLI Approach

First, be sure that your maven settings.xml is properly configured to authenticate with the target repository and that your local maven 
repository is as clean as you want the target repository to be. Then you can execute the plugin directly:

    mvn net.adamcin:blunderbuss-maven-plugin:0.4.0:sync \
        -DindexGroupId=com.myorg1.ado \
        -DindexArtifactId=my-index \
        -DaltDeploymentRepository=MyFeedInOrg1::https://pkgs.dev.azure.com/OrganzationName/ProjectName/_packaging/MyProjectScopedFeed1/Maven/v1
        

### Executing in a Maven Module Directory

You can also configure the blunderbuss plugin in a module pom's `pluginManagement` section, so that these details don't need to be repeated as
CLI arguments when you run the `sync` goal in the same module directory.

For example, after adding the following to your pom, it configures the plugin with the same details in the earlier CLI-only example:

    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>net.adamcin</groupId>
                <artifactId>blunderbuss-maven-plugin</artifactId>
                <version>0.4.0</version>
                <configuration>
                    <indexGroupId>com.myorg1.ado</indexGroupId>
                    <indexArtifactId>my-index</indexArtifactId>
                    <altDeploymentRepository>MyFeedInOrg1::https://pkgs.dev.azure.com/OrganzationName/ProjectName/_packaging/MyProjectScopedFeed1/Maven/v1</altDeploymentRepository>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
    
This allows you to run a much simpler command:

    mvn net.adamcin:blunderbuss-maven-plugin:sync
    
