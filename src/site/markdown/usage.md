Usage
=====

## Pure CLI Approach

First, be sure that your maven settings.xml is properly configured to authenticate with the target repository and that your local maven 
repository is as clean as you want the target repository to be. Then you can execute the plugin directly:

    mvn net.adamcin:blunderbuss-maven-plugin:0.2.0:sync \
        -DindexGroupId=com.myorg1.ado \
        -DindexArtifactId=my-index \
        -DaltDeploymentRepository=MyFeedInOrg1::https://pkgs.dev.azure.com/OrganzationName/ProjectName/_packaging/MyProjectScopedFeed1/Maven/v1
        

## Executing in a Maven Module Directory

You can also configure the blunderbuss plugin in a module pom's `pluginManagement` section, so that these details don't need to be repeated as
CLI arguments when you run the `sync` goal in the same module directory.

For example, after adding the following to your pom, it configures the plugin with the same details in the earlier CLI-only example:

    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>net.adamcin</groupId>
                <artifactId>blunderbuss-maven-plugin</artifactId>
                <version>0.2.0</version>
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
    
