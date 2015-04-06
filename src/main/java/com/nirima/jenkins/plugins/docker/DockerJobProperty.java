package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import com.google.common.base.Strings;
import hudson.util.FormValidation;
import java.util.regex.Pattern;
import org.kohsuke.stapler.QueryParameter;


public class DockerJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {
    
    /**
     * Tag on completion (commit).
     */
    public final boolean tagOnCompletion;
    public final boolean cleanImages;

    /**
     * Keep containers running after successful builds
     */
    public final boolean remainsRunning;

    /**
     * Author of the image
     */
    public final String imageAuthor;

    /**
     * Whether to tag the committed image as 'latest'
     */
    public final boolean tagLatest;

    /**
     * Whether to tag the committed image with the build number
     */
    public final boolean tagBuildNumber;

    /**
     * Repository name to use for the image, including domain/namespace
     */
    public final String repositoryName;

    /** 
     * List of tags, delimited by commas or semi-colons
     */
    public final String imageTags;

    /**
     * Kept for backwards compatibility with existing data
     */
    public final String additionalTag;


    @DataBoundConstructor
    public DockerJobProperty(
            boolean tagOnCompletion, 
            boolean cleanImages,
            boolean remainsRunning,
            String imageAuthor,
            boolean tagLatest,
            boolean tagBuildNumber,
            String repositoryName,
            String imageTags ) 
    {
        this.additionalTag = "";
        this.tagOnCompletion = tagOnCompletion;
        this.cleanImages = cleanImages;
        this.remainsRunning = remainsRunning;
        this.imageAuthor = imageAuthor;
        this.tagLatest = tagLatest;
        this.tagBuildNumber = tagBuildNumber;
        this.repositoryName = repositoryName;
        this.imageTags = imageTags;
        
    }

    @Exported
    public boolean isPushOnSuccess() {
        return false;
    }

    @Exported
    public boolean isTagOnCompletion() {
        return tagOnCompletion;
    }

    @Exported
    public boolean isCleanImages() {
        return cleanImages;
    }

    @Exported
    public boolean isRemainsRunning() {
        return remainsRunning;
    }

    @Exported
    public String getImageAuthor() {
        return imageAuthor;
    }

    @Exported
    public boolean isTagLatest() {
        return tagLatest;
    }

    @Exported
    public boolean isTagBuildNumber() {
        return tagBuildNumber;
    }

    @Exported 
    public String getRepositoryName() {
        return repositoryName;
    }

    @Exported 
    public String getImageTags() {
        /*
         * Adds the additionalTag string here to maintain backward compatibility.
         * Remove refs in stable?
         */
        if(!Strings.isNullOrEmpty(additionalTag)) {
            return additionalTag + "," + imageTags;
        } else {
            return imageTags;
        }
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public String getDisplayName() {
            return "Docker Job Properties";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        public FormValidation doCheckRepositoryName(@QueryParameter String repositoryName) {
            if(repositoryName == null || repositoryName.length() == 0) {
                return FormValidation.ok();
            }
            String registry = null;
            String namespace = null;
            String repository = null;
            
            String[] nameParts = repositoryName.split("/");
            
            // Only contains repository name
            if(nameParts.length == 1) {
                repository = nameParts[0];
                // namespace = "library";
                // registry = "docker.io";
            // Contains registry/repository or namespace/repository
            } else if(nameParts.length == 2) {
                if(nameParts[0].contains(":") ||
                   nameParts[0].contains(".") ||
                   nameParts[0].equals("localhost")){
                    // registry/repository
                    registry = nameParts[0];
                    // namespace = "library";
                } else {
                    // namespace/repository
                    // registry = "docker.io";
                    namespace = nameParts[0];
                }
                repository = nameParts[1];
                
            // Full 3 part registry/namespace/repository
            } else if (nameParts.length == 3) {
                registry = nameParts[0];
                namespace = nameParts[1];
                repository = nameParts[2];
            // Can't contain more than 3
            } else if (nameParts.length > 3) {
                return FormValidation.error(
                        "More than 3 parts in registry name"
                );
            }
            
            // Docker doesn't actually check anything else yet
            if(registry != null) {
                if(registry.contains("://")) {
                    return FormValidation.error("Registry should not contain schema");
                }    
            }
            
            // Validate namespace if specified
            if(namespace != null){
                Pattern namespaceRegexp = Pattern.compile("^([a-z0-9-_]*)$");
                if(!namespaceRegexp.matcher(namespace).matches()){
                    return FormValidation.error(
                            "Invalid namespace:" + namespace
                    );
                }
                // Check namespace length
                if(namespace.length() < 2 || namespace.length() > 255) {
                    return FormValidation.error(
                            "Namespace must be between 2 and 255 characters long"
                    );
                }
                // No start/end with hyphon
                if(namespace.startsWith("-") || namespace.endsWith("-")) {
                    return FormValidation.error(
                            "Namespace cannot start or end with a hyphon"
                    );
                }
                // No double hyphons
                if(namespace.contains("--")) {
                    return FormValidation.error(
                            "Namespace cannot contain consecutive hyphons"
                    );
                }
            }
            
            // We will always have a value for registry by now
            // Check that it doesn't have a tag
            if(repository.contains(":")) {
                return FormValidation.error("Do not include tags here");
            }
            
            Pattern repositoryRegexp = Pattern.compile("^([a-z0-9-_.]+)$");
            if(!repositoryRegexp.matcher(repository).matches()) {
                return FormValidation.error("Invalid repository name");
            }
            
            if(registry != null) {
                return FormValidation.ok("Using private registry " + registry);
            }
            
            return FormValidation.ok();
        }

        public FormValidation doCheckImageTags(@QueryParameter String imageTags) {
            if(imageTags == null || imageTags.length() == 0) {
                return FormValidation.ok();
            }
            String[] tags = imageTags.split("[,:;]");
            Pattern tagRegexp = Pattern.compile("^[\\w][\\w.-]{0,127}$");
            for(String tag : tags) {
                if(!tagRegexp.matcher(tag).matches()) {
                    return FormValidation.error("Invalid tag: " + tag);
                }
            }
            return FormValidation.ok();
        }

        @Override
        public DockerJobProperty newInstance(StaplerRequest sr, JSONObject formData) 
        throws hudson.model.Descriptor.FormException {

            return new DockerJobProperty(
                    (Boolean)formData.get("tagOnCompletion"),
                    (Boolean)formData.get("cleanImages"),
                    (Boolean)formData.get("remainsRunning"),
                    (String)formData.get("imageAuthor"),
                    (Boolean)formData.get("tagLatest"),
                    (Boolean)formData.get("tagBuildNumber"),
                    (String)formData.get("repositoryName"),
                    (String)formData.get("imageTags")
                    );
        }
    }
}
