package com.nirima.jenkins.plugins.docker.utils;

import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

/**
 * For constructing and validating docker image names, and their respective parts:
 * [REGISTRY/][NAMESPACE/]REPOSITORY[:TAG]
 * 
 * Based on Docker v1.5 source
 * @author Joshua Griffiths 08/04/2015
 */


public class DockerImageName {
    
    private TagName tag;
    private RepositoryName repository;
    private NamespaceName namespace;
    private RegistryName registry;
    
    // Rather than pass null as String
    public DockerImageName() {}
    
    /**
     * De-constructs given image name into relevant objects
     * @param imageName Full image name for docker image, or null
     */
    public DockerImageName(String imageName) throws DockerImageNameException {
        if (Strings.isNullOrEmpty(imageName)) {
            return;
        }
        // Split the name into a map and assign each name parts. 
        // This action will throw an exception if the name contains too many parts.
        Map<String, String> nameMap = splitImageName(imageName);
        if (nameMap.get("registry") != null) {
            registry = new RegistryName(nameMap.get("registry"));
        }
        if (nameMap.get("namespace") != null) {
            namespace = new NamespaceName(nameMap.get("namespace"));
        }
        if (nameMap.get("repository") != null) {
            repository = new RepositoryName(nameMap.get("repository"));
        }
        if (nameMap.get("tag") != null) {
            tag = new TagName(nameMap.get("tag"));
        }
    }
    
    public TagName getTag() {
        return tag;
    }
    
    public RepositoryName getRepository() {
        return repository;
    }
    
    public NamespaceName getNamespace() {
        return namespace;
    }
    
    public RegistryName getRegistry() {
        return registry;
    }
    
    /**
     * @param tag String
     * @return DockerImageName
     */
    public DockerImageName withTag(String tag) {
        this.tag = new TagName(tag);
        return this;
    }
    
    /**
     * @param tag TagName object
     * @return DockerImageName
     */
    public DockerImageName withTag(TagName tag) {
        this.tag = tag;
        return this;
    }
    
    /**
     * @param repository String
     * @return DockerImageName
     */
    public DockerImageName withRepository(String repository) {
        this.repository = new RepositoryName(repository);
        return this;
    }
    
    /**
     * @param repository RepositoryName object
     * @return DockerImageName
     */
    public DockerImageName withRepository(RepositoryName repository) {
        this.repository = repository;
        return this;
    }
    
    /**
     * @param namespace String
     * @return DockerImageName
     */
    public DockerImageName withNamespace(String namespace) {
        this.namespace = new NamespaceName(namespace);
        return this;
    }
    
    /**
     * @param namespace NamespaceName object
     * @return DockerImageName
     */
    public DockerImageName withNamespace(NamespaceName namespace) {
        this.namespace = namespace;
        return this;
    }
    
    /**
     * @param registry String
     * @return DockerImageName
     */
    public DockerImageName withRegistry(String registry) {
        this.registry = new RegistryName(registry);
        return this;
    }
    
    /**
     * @param registry RegistryName object
     * @return DockerImageName
     */
    public DockerImageName withRegistry(RegistryName registry) {
        this.registry = registry;
        return this;
    }
    
    /**
     * Joins the image parts which aren't null to return a (possibly) valid name
     * @return String containing image name parts
     */
    @Override
    public String toString() {
        String imageNameStr = "";
        if (registry != null) {
            imageNameStr += (registry + "/");
        }
        if (namespace != null) {
            imageNameStr += (namespace + "/");
        }
        if (repository != null) {
            imageNameStr += repository;
        }
        if (tag != null) {
            imageNameStr += (":" + tag);
        }
        return imageNameStr;
    }
    
    /**
     * Verifies whether an image name is valid. It must contain a repository name to be valid.
     * @return Validity of image name
     */
    public boolean isValid() {
        return (repository != null && repository.isValid()) && (tag == null || tag.isValid()) 
                && (registry == null || registry.isValid()) && (namespace == null || namespace.isValid());
    }
    
    /**
     * Each populated part of the image name is made valid, if possible
     * @return Valid DockerImageName part
     */
    public DockerImageName makeValid() {
        if (registry != null) {
            registry = registry.makeValid();
        }
        if (namespace != null) {
            namespace = namespace.makeValid();
        }
        if (repository != null) {
            repository = repository.makeValid();
        }
        if (tag != null) {
            tag = tag.makeValid();
        }
        return this;
    }
    
    /**
     * Checks that image is valid, or throws a helpful exception as to why
     */
    public void validate() throws DockerImageNameException {
        if (repository == null) {
            throw new DockerImageNameException("Invalid image name: Repository cannot be null");
        }
        repository.validate();
        if (tag != null) {
            tag.validate();
        }
        if (namespace != null) {
            namespace.validate();
        }
        if (registry != null) {
            registry.validate();
        }
    }
    
    /**
     * Splits image name into name parts as cleanly as possible, avoiding any kind of validation
     * @param imageName Full docker image name
     * @return Map of image name parts
     * @throws DockerImageException
     */
    private static Map<String, String> splitImageName(String imageName) throws DockerImageNameException {
        String registryName = null;
        String namespaceName = null;
        String repositoryName = null;
        String tagName = null;
        String repositoryNameWithTag;
        
        // Check for a schema (://) at the start and ignore it when splitting
        String[] nameParts;
        int schemaIndex = imageName.lastIndexOf("://");
        if (schemaIndex > -1) {
            nameParts = imageName.substring(schemaIndex + 3).split("/");
            nameParts[0] = imageName.substring(0, schemaIndex + 3) + nameParts[0];
        } else {
            nameParts = imageName.split("/");
        }
        
        // This should never happen. Ever.
        if (nameParts.length == 0) {
            throw new DockerImageNameException("Cannot parse empty image name");
        }
        
        Map<String, String> nameMap = new HashMap<String, String>();
        // Break up the 3 components with slashes. 
        switch (nameParts.length) {
            case 1: 
                repositoryNameWithTag = nameParts[0];
                break;
            case 2:
                repositoryNameWithTag = nameParts[1];
                if (nameParts[0].contains(".") || nameParts[0].contains(":") || nameParts[0].equals("localhost")) {
                    registryName = nameParts[0];
                } else {
                    namespaceName = nameParts[0];
                }
                break;
            case 3:
                repositoryNameWithTag = nameParts[2];
                namespaceName = nameParts[1];
                registryName = nameParts[0];
                break;
            default:
                namespaceName = nameParts[nameParts.length - 2];
                repositoryNameWithTag = nameParts[nameParts.length - 1];
                registryName = "";
                for (int i = 0; i< (nameParts.length -2); i++) {
                    registryName += (nameParts[i] + "/");
                }
            registryName = StringUtils.chop(registryName);
        }
        int lastColonPoint = repositoryNameWithTag.lastIndexOf(":");
        // If the first char is a ':', treat it as the repository and we'll validate it later
        if (lastColonPoint > 0) {
            repositoryName = repositoryNameWithTag.substring(0, lastColonPoint);
            tagName = repositoryNameWithTag.substring(lastColonPoint + 1);
        }
        nameMap.put("tag", tagName);
        nameMap.put("repository", StringUtils.defaultIfEmpty(repositoryName, repositoryNameWithTag));
        nameMap.put("namespace", namespaceName);
        nameMap.put("registry", registryName);
        return nameMap;
    }
    
    /**
     * Abstract class for each image name part to extend
     */
    private static abstract class AbstractDockerImageName {
        
        protected String partName;
        protected String partRegexp; // For matching
        protected String partInvertedRegexp; // For replacing
        
        /**
         * Abstract class constructor for use in subclasses
         * @param namePart 
         */
        public AbstractDockerImageName(String namePart) {
            if (namePart == null || namePart.isEmpty()) {
                throw new IllegalArgumentException("Illegal use of empty/null name part");
            }
            partName = namePart;
        }
        
        @Override
        public String toString() {
            return partName;
        }
        
        /**
         * Removes any non-docker-friendly characters from the image part
         */
        public abstract AbstractDockerImageName makeValid();
        
        /**
         * Checks whether the image name part is valid
         * @return whether image name part is valid
         */
        public abstract boolean isValid();
        
        /**
         * Validates the image name part and throws DockerImageNameException, with reason, if invalid
        */
        public abstract void validate() throws DockerImageNameException;
        
        public String spacesToHyphens(String str) {
            return str.replaceAll("[\\s]+", "-");
        }
    }
    
    public static class RegistryName extends AbstractDockerImageName {
        
        /**
         * Calls constructor on abstract to set namePart
         * @param namePart Part of image name
         */
        public RegistryName(String namePart) {
            super(namePart);
        }
        
        @Override
        public void validate() {
            if (partName.contains("://")) { 
                throw new DockerImageNameException("Invalid registry: Registry must not contain a schema");
            }
        }
        
        @Override 
        public boolean isValid() {
            return !partName.contains("://");
        }
        
        @Override
        public RegistryName makeValid() {
            if (!isValid()) {
                removeSchema();
            }
            return this;
        }
        
        private void removeSchema() {
            int lastSchemaInstance = partName.lastIndexOf("://");
            if (lastSchemaInstance > -1) {
                partName = partName.substring(lastSchemaInstance + 3);  // Remove the 3 schema chars too.
            }
        }
    }
    
    public static class NamespaceName extends AbstractDockerImageName {
        
        /**
         * Calls constructor on abstract to set namePart
         * @param namePart Part of image name
         */
        public NamespaceName(String namePart) {
            super(namePart);
            this.partRegexp = "^([a-z0-9_-]*)$";
            this.partInvertedRegexp = "[^a-z0-9_-]";
        }
        
        @Override
        public void validate() {
            if (partName.length() < 2 ||partName.length() > 255) {
                throw new DockerImageNameException("Invalid namespace: Namespace must be between 2 and 255 characters");
            }
            if (partName.startsWith("-") || partName.endsWith("-")) {
                throw new DockerImageNameException("Invalid namespace: Namespace must not begin or end with a hyphon");
            }
            if (partName.contains("--")) {
                throw new DockerImageNameException("Invalid namespace: Namespace must not contain consecutive hyphons");
            }
            if (!partName.matches(partRegexp)) {
                throw new DockerImageNameException("Invalid namespace: Namespace must match [a-z0-9_-]");
            }
        }
        
        @Override
        public boolean isValid() {
            return partName.matches(partRegexp) && !partName.contains("--") 
                    && !partName.startsWith("-") && !partName.endsWith("-") && partName.length() < 255
                    && partName.length() > 2;
        }
        
        @Override
        public NamespaceName makeValid() {
            if (partName.length() > 255) {
                partName = partName.substring(0, 255);
            }
            if (!partName.matches(partRegexp)) {
                partName = spacesToHyphens(partName)
                        .toLowerCase()
                        .replaceAll(partInvertedRegexp, "");
            }
            while (partName.contains("--")) {
                partName = partName.replaceAll("--", "-");
            }
            partName = partName.replaceAll("^-", "").replaceAll("-$", "");
            while (partName.length() < 2) {
                partName += "0";
            }
            return this;
        }
    }
    
    public static class RepositoryName extends AbstractDockerImageName {
        
        /**
         * @param namePart Part of image name
         */
        public RepositoryName(String namePart) {
            super(namePart);
            partRegexp = "^([a-z0-9_.-]*)$";
            partInvertedRegexp = "[^a-z0-9_.-]";
        }
        
        @Override
        public void validate() {
            if (!partName.matches(partRegexp)) {
                throw new DockerImageNameException("Invalid registry: Registry must match [a-z0-9_.-]");
            }
        }
        
        @Override
        public boolean isValid() {
            return partName.matches(partRegexp);
        }
        
        @Override
        public RepositoryName makeValid() {
            if (!isValid()) {
                partName = spacesToHyphens(partName)
                        .toLowerCase()
                        .replaceAll(partInvertedRegexp, "");
            }
            return this;
        }
    }
    
    public static class TagName extends AbstractDockerImageName {
        
        /**
         * @param namePart Part of image name
         */
        public TagName(String namePart) {
            super(namePart);
            partRegexp = "^[a-zA-Z0-9][a-zA-Z0-9_.-]*$";
            partInvertedRegexp = "[^a-zA-Z0-9_.-]";
        }
        
        @Override
        public void validate() {
            if (!partName.matches(partRegexp)) {
                throw new DockerImageNameException("Invalid tag: Tag must match [a-zA-Z0-9][a-zA-Z0-9_.-]*");
            }
            if (partName.length() > 128) {
                throw new DockerImageNameException("Invalid tag: Tag must be between 1 and 128 characters");
            }
        }
        
        @Override
        public boolean isValid() {
            return partName.matches(partRegexp) && partName.length() < 128;
        }
        
        @Override
        public TagName makeValid() {
            if (partName.length() > 128) {
                partName = partName.substring(0, 128);
            }
            partName = partName.replaceAll("^[-_.]+", "");
            if (!partName.matches(partRegexp)) {
                partName = spacesToHyphens(partName).replaceAll(partInvertedRegexp, "");
            }
            return this;
        }   
    }
}
