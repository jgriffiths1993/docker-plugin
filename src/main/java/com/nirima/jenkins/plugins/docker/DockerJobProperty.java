package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.regex.Pattern;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;


public class DockerJobProperty extends JobProperty<AbstractProject<?, ?>> {
    
    // Empty constructor - using DataBoundSetter instead
    @DataBoundConstructor public DockerJobProperty() {}
    
    // Commit container when build completes
    @DataBoundSetter public boolean commitContainer;
    
    // Remove committed image
    @DataBoundSetter public boolean cleanImages;
    
    //Keep containers running after successful builds
    @DataBoundSetter public boolean remainsRunning;

    //Author of the image
    @DataBoundSetter public String imageAuthor;

    // Tag committed image as 'latest'
    @DataBoundSetter public boolean tagLatest;
    
    // Tag the committed image with the build number
    @DataBoundSetter public boolean tagBuildNumber;
    
    // Repository name to use for the image, including domain/namespace
    @DataBoundSetter public String repositoryName;

    // List of tags, delimited by commas or semi-colons
    @DataBoundSetter public String imageTags;
    
     // Kept for backwards compatibility with existing data
    @DataBoundSetter public String additionalTag;

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
            if( repositoryName == null || repositoryName.length() == 0 ) {
                return FormValidation.ok();
            }
            String registry = null;
            String namespace = null;
            String repository = null;
            
            String[] nameParts = repositoryName.split("/");
            
            // Only contains repository name
            if( nameParts.length == 1 ) {
                repository = nameParts[0];

            // Contains registry/repository or namespace/repository
            } else if( nameParts.length == 2 ) {
                if( nameParts[0].contains(":") || nameParts[0].contains(".") || nameParts[0].equals("localhost") ) {
                    registry = nameParts[0];
                } else {
                    namespace = nameParts[0];
                }
                repository = nameParts[1];
                
            // Full 3 part registry/namespace/repository
            } else if( nameParts.length == 3 ) {
                registry = nameParts[0];
                namespace = nameParts[1];
                repository = nameParts[2];

            // Can't contain more than 3
            } else if( nameParts.length > 3 ) {
                return FormValidation.error("More than 3 parts in registry name");
            }
            
            // Docker doesn't actually check anything else yet
            if( registry != null ) {
                if( registry.contains("://") ) {
                    return FormValidation.error("Registry should not contain schema");
                }    
            }
            
            // Validate namespace if specified
            if(namespace != null){
                Pattern namespaceRegexp = Pattern.compile("^([a-z0-9-_]*)$");
                if(!namespaceRegexp.matcher(namespace).matches()){
                    return FormValidation.error("Invalid namespace:" + namespace);
                }
                // Check namespace length
                if(namespace.length() < 2 || namespace.length() > 255) {
                    return FormValidation.error("Namespace must be between 2 and 255 characters long");
                }
                // No start/end with hyphon
                if(namespace.startsWith("-") || namespace.endsWith("-")) {
                    return FormValidation.error("Namespace cannot start or end with a hyphon");
                }
                // No double hyphons
                if(namespace.contains("--")) {
                    return FormValidation.error("Namespace cannot contain consecutive hyphons");
                }
            }
            
            // We will always have a value for registry by now
            // Check that it doesn't have a tag
            if(repository.contains(":")) {
                return FormValidation.error("Do not include tags here");
            }
            // Check that it matches docker's regexp
            Pattern repositoryRegexp = Pattern.compile("^([a-z0-9-_.]+)$");
            if(!repositoryRegexp.matcher(repository).matches()) {
                return FormValidation.error("Invalid repository name");
            }
            // Let the user know that they're using a private registry in their name
            if(registry != null) {
                return FormValidation.ok("Using private registry " + registry);
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckImageTags(@QueryParameter String imageTags) {
            // It's allowed to be empty
            if(imageTags == null || imageTags.length() == 0) {
                return FormValidation.ok();
            }
            // Split them by the possible delimiters
            String[] tags = imageTags.split("[,:;]");
            // Iterate the tags and match them, or return a validation error
            Pattern tagRegexp = Pattern.compile("^[\\w][\\w.-]{0,127}$");
            for(String tag : tags) {
                if(!tagRegexp.matcher(tag).matches()) {
                    return FormValidation.error("Invalid tag: " + tag);
                }
            }
            return FormValidation.ok();
        }
        
        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (req.hasParameter("commitContainer") || req.hasParameter("remainsRunning")) {
                return req.bindJSON(DockerJobProperty.class, formData);
            }
            return null;
        }
    }
}
