package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.NotModifiedException;
import com.github.dockerjava.api.NotFoundException;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;

import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DockerSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(
            DockerSlave.class.getName()
    );

    public final DockerTemplate dockerTemplate;
    public final String containerId;

    private transient Run theRun;

    @DataBoundConstructor
    public DockerSlave(DockerTemplate dockerTemplate, String containerId, String name, String nodeDescription, String remoteFS, int numExecutors, Mode mode, Integer memoryLimit, Integer cpuShares, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        Preconditions.checkNotNull(dockerTemplate);
        Preconditions.checkNotNull(containerId);

        this.dockerTemplate = dockerTemplate;
        this.containerId = containerId;
    }

    public DockerCloud getCloud() {
        DockerCloud theCloud = dockerTemplate.getParent();

        if(theCloud == null) {
            throw new RuntimeException("Docker template " + dockerTemplate + " has no parent ");
        }

        return theCloud;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    public void setRun(Run run) {
        this.theRun = run;
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    @Override
    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        if (item.task instanceof Queue.FlyweightTask) {
          return new CauseOfBlockage() {
            public String getShortDescription() {
                return "Don't run FlyweightTask on Docker node";
            }
          };
        }
        return super.canTake(item);
    }

    public boolean containerExistsInCloud() {
        try {
            DockerClient client = getClient();
            client.inspectContainerCmd(containerId).exec();
            return true;
        } catch(Exception ex) {
            return false;
        }
    }
    
    private void stopContainer() {
        DockerClient client = getClient();
        LOGGER.log(Level.INFO, "Stopping container: {0}", containerId);
        try {
            client.stopContainerCmd(containerId).exec();
        } catch(NotModifiedException ex) {
            LOGGER.log(Level.INFO, 
                    "Could not stop container: {0}. Not running.", 
                    containerId
            );
        } catch (NotFoundException ex) {
            LOGGER.log(Level.INFO,
                    "Could not stop container: {0}. Doesn't exist.",
                    containerId
            );
        }
    }

    @Override
    protected void _terminate(TaskListener listener) 
            throws IOException, InterruptedException {

        try {
            // Disconnect slave from Jenkins
            toComputer().disconnect(null);

            // Stop container if "remainsRunning" isn't ticked.
            if(!getJobProperty().isRemainsRunning()) {
                stopContainer(); 
            }

            // If the run was OK, then do any tagging here
            // TODO: add options to tag unsuccessful builds
            if(theRun != null) {
                try {
                    slaveShutdown(listener);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failure to slaveShutdown instance " + containerId+ " for slave " + name , e);
                }
            }

            // If we aren't stopping the container, then don't remove it either.
            if(!getJobProperty().isRemainsRunning()) {
                try {
                    DockerClient client = getClient();
                    client.removeContainerCmd(containerId).exec();
                } catch(Exception ex) {
                    LOGGER.log(Level.SEVERE, "Failed to remove instance " + containerId + " for slave " + name + " due to exception",ex);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance " + containerId + " for slave " + name ,e);
        }
    }

    private void slaveShutdown(TaskListener listener) throws DockerException, IOException {

        String imageId = null;
        if(getJobProperty().isTagOnCompletion()) {
            imageId = commitContainer();
        }
        addJenkinsAction(imageId);
        

        if(getJobProperty().isCleanImages() && imageId != null) {
            cleanImages(imageId);
        }
    }
    
    private void cleanImages(String imageId) {
        DockerClient client = getClient();
        client.removeImageCmd(imageId)
                .withForce()
                .exec();
    }
    
    private String commitContainer() {
        DockerClient client = getClient();

        // Tag with job name if no repository name is given.
        String repositoryName;
        if(Strings.isNullOrEmpty(getJobProperty().getRepositoryName())){
            repositoryName = getJobName();
        } else {
            repositoryName = getJobProperty().getRepositoryName();
        }
        
        LOGGER.log(Level.INFO, "Tagging with repository: {0}", repositoryName);

        // Get our list of tags, or use the build number
        ArrayList<String> imageTags = new ArrayList<String>(
                Arrays.asList(
                    getJobProperty().getImageTags().split("[,;:]")
                    ));
        
        // Tag latest if specified
        if(getJobProperty().isTagLatest()) {
            imageTags.add("latest");
        }

        // If there's no tags, or if specified, tag with the build number
        if(imageTags.isEmpty() || getJobProperty().isTagBuildNumber()) {
            imageTags.add(getBuildNumber());
        }

        // Commit image and get new image ID
        String imageId = client.commitCmd(containerId)
                    .withAuthor(getJobProperty().getImageAuthor())
                    .exec();
        LOGGER.log(Level.INFO, "Commited to image: {0}", imageId);

        // Iterate list of tags, and tag appropriately
        for(String tag : imageTags) {
            // Skip empty tags
            if(Strings.isNullOrEmpty(tag)){
                continue;
            }
            LOGGER.log(Level.INFO, "Tagging with: {0}", tag);

            // Tag the image!
            try {
                client.tagImageCmd(imageId, repositoryName, tag)
                    .withForce()
                    .exec();
            } catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Could not add tag: {0}", tag);
            }
        }
        return imageId;
    }

    /**
     * Returns the job name without any non-docker-friendly characters
     */
    private String getJobName() {
        String jobName = theRun.getParent().getDisplayName();
        return jobName.toLowerCase()
                             .replaceAll("\\s", "_")
                             .replaceAll("[^/a-z0-9-_.]", "");
    }

    /**
     * Returns the build number without any non-docker-friendly characters
     */
    private String getBuildNumber() {
        String buildNumber = theRun.getDisplayName();
        return buildNumber.replaceAll("\\s", "_")
                          .replaceAll("[^A-Za-z0-9_.-]", "");
    }
    
    /**
     * Add a built on docker action.
     * @param tag_image
     * @throws IOException
     */
    private void addJenkinsAction(String tag_image) throws IOException {
        theRun.addAction(
                new DockerBuildAction(
                    getCloud().serverUrl, 
                    containerId, 
                    tag_image, 
                    dockerTemplate.remoteFsMapping) 
                );
        theRun.save();
    }

    public DockerClient getClient() {
        return getCloud().connect();
    }

    /**
     * Called when the slave is connected to Jenkins
     */
    public void onConnected() {

    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("containerId", containerId)
                .add("template", dockerTemplate)
                .toString();
    }

    private DockerJobProperty getJobProperty() {

        try {
            DockerJobProperty p = (DockerJobProperty) ((AbstractBuild) theRun).getProject().getProperty(DockerJobProperty.class);

            if (p != null)
                return p;
        } catch(Exception ex) {
            // Don't care.
        }
        // Safe default
        return new DockerJobProperty(false, false, false, null, false, false, null, null);

    }

    @Extension
	public static final class DescriptorImpl extends SlaveDescriptor {

    	@Override
		public String getDisplayName() {
			return "Docker Slave";
    	};

		@Override
		public boolean isInstantiable() {
			return false;
		}

	}
}
