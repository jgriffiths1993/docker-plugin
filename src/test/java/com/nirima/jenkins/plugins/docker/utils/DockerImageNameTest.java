package com.nirima.jenkins.plugins.docker.utils;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 *
 * @author Joshua Griffiths 10/04/2015
 */

public class DockerImageNameTest {
    
    @Test
    public void chainStringConstruction() {
        DockerImageName imageName = new DockerImageName()
                .withNamespace("library")
                .withRepository("ubuntu")
                .withRegistry("docker.io")
                .withTag("trusty");
        assertEquals(imageName.toString(), "docker.io/library/ubuntu:trusty");
        assertEquals(imageName.makeValid().toString(), "docker.io/library/ubuntu:trusty");
    }
    
    @Test
    public void stringConstruction() {
        DockerImageName imageName = new DockerImageName("docker.io/library/ubuntu:trusty");
        assertEquals(imageName.toString(), "docker.io/library/ubuntu:trusty");
        assertEquals(imageName.makeValid().toString(), "docker.io/library/ubuntu:trusty");
    }
    
    @Test
    public void chainConstruction() {
        DockerImageName.RepositoryName repository = new DockerImageName.RepositoryName("ubuntu");
        DockerImageName.NamespaceName namespace = new DockerImageName.NamespaceName("library");
        DockerImageName.RegistryName registry = new DockerImageName.RegistryName("docker.io");
        DockerImageName.TagName tag = new DockerImageName.TagName("trusty");
        DockerImageName imageName = new DockerImageName()
                .withNamespace(namespace)
                .withRepository(repository)
                .withRegistry(registry)
                .withTag(tag);
        assertEquals(imageName.toString(), "docker.io/library/ubuntu:trusty");
        assertEquals(imageName.makeValid().toString(), "docker.io/library/ubuntu:trusty");
    }
    
    @Test
    public void imageNameBreakdown() {
        String normalName = "docker.io/library/ubuntu:latest";
        String withSchema = "https://docker.io/library/ubuntu:latest";
        String noRegistry = "library/ubuntu:latest";
        String noNamespace = "docker.io/ubuntu:latest";
        String noTag = "docker.io/library/ubuntu";
        String tooManyParts = "docker.io/whoops!/library/ubuntu:latest";
        
        DockerImageName image = new DockerImageName(normalName);
        DockerImageName ws = new DockerImageName(withSchema);
        DockerImageName nr = new DockerImageName(noRegistry);
        DockerImageName nn = new DockerImageName(noNamespace);
        DockerImageName nt = new DockerImageName(noTag);
        DockerImageName tmp = new DockerImageName(tooManyParts);
        
        assertEquals(image.getRegistry().toString(), "docker.io");
        assertEquals(image.getNamespace().toString(), "library");
        assertEquals(image.getRepository().toString(), "ubuntu");
        assertEquals(image.getTag().toString(), "latest");
        
        assertEquals(ws.getRegistry().toString(), "https://docker.io");
        assertEquals(ws.getNamespace().toString(), "library");
        assertEquals(ws.getRepository().toString(), "ubuntu");
        assertEquals(ws.getTag().toString(), "latest");
        
        assertEquals(nr.getNamespace().toString(), "library");
        assertEquals(nr.getRepository().toString(), "ubuntu");
        assertEquals(nr.getTag().toString(), "latest");
        
        assertEquals(nn.getRegistry().toString(), "docker.io");
        assertEquals(nn.getRepository().toString(), "ubuntu");
        assertEquals(nn.getTag().toString(), "latest");
        
        assertEquals(nt.getRegistry().toString(), "docker.io");
        assertEquals(nt.getNamespace().toString(), "library");
        assertEquals(nt.getRepository().toString(), "ubuntu");
        
        assertEquals(tmp.getRegistry().toString(), "docker.io/whoops!");
        assertEquals(tmp.getNamespace().toString(), "library");
        assertEquals(tmp.getRepository().toString(), "ubuntu");
        assertEquals(tmp.getTag().toString(), "latest");
    }
    
    @Test
    public void cleanRegistry() {
        DockerImageName image = new DockerImageName("https://docker.io/library/ubuntu:latest");
        assertEquals(image.toString(), "https://docker.io/library/ubuntu:latest");
        assertEquals(image.getRegistry().toString(), "https://docker.io");
        assertFalse(image.isValid());
        assertFalse(image.getRegistry().isValid());
        image = image.withRegistry(image.getRegistry().makeValid());
        assertEquals(image.toString(), "docker.io/library/ubuntu:latest");
        assertEquals(image.getRegistry().toString(), "docker.io");
    }
    
    @Test
    public void cleanNamespace() {
        DockerImageName image = new DockerImageName("--L$1_Br--4---r  y-/ubuntu");
        assertFalse(image.isValid());
        assertFalse(image.getNamespace().isValid());
        assertEquals(image.getNamespace().toString(), "--L$1_Br--4---r  y-");
        assertTrue(image.getNamespace().makeValid().isValid());
        assertEquals(image.getNamespace().toString(), "l1_br-4-r-y");
    }
    
    @Test
    public void cleanRepository() {
        DockerImageName image = new DockerImageName("library/U8u**$ @email@@#~!\"£$%^&*n2");
        assertFalse(image.isValid());
        assertFalse(image.getRepository().isValid());
        assertEquals(image.getRepository().toString(), "U8u**$ @email@@#~!\"£$%^&*n2");
        assertTrue(image.getRepository().makeValid().isValid());
        assertEquals(image.getRepository().toString(), "u8u-emailn2");
    }
    
    @Test
    public void cleanTag() {
        DockerImageName image = new DockerImageName("library/ubuntu:_-_-_H3LL0_**$$%^l.T35t");
        assertFalse(image.isValid());
        assertFalse(image.getTag().isValid());
        assertEquals(image.getTag().toString(), "_-_-_H3LL0_**$$%^l.T35t");
        assertTrue(image.getTag().makeValid().isValid());
        assertEquals(image.getTag().toString(), "H3LL0_l.T35t");
    }
    
    @Test(expected=NullPointerException.class)
    public void nullPointers() {
        DockerImageName image = new DockerImageName();
        assertNull(image.getRepository());
        image.getRepository().toString();
    }
    
    @Test
    public void persistance() {
        DockerImageName image = new DockerImageName("library/ubuntu");
        DockerImageName imageTwo = image.withRegistry("docker.io");
        assertTrue(image == imageTwo);
    }
    
    @Test(expected=DockerImageNameException.class)
    public void pleaseDontDoThis() {
        DockerImageName image1 = new DockerImageName("///////////////");
    }
    
    @Test
    public void emptyImage() {
        DockerImageName image2 = new DockerImageName("");
        DockerImageName image3 = new DockerImageName(null);
    }
    
    @Test
    public void forFormValidation() {
        DockerImageName image = new DockerImageName("library/ubuntu").withTag("£$%^&*");
        String exceptionMessage = "";
        try {
            image.getTag().validate();
        } catch (DockerImageNameException e) {
            exceptionMessage = e.getMessage();
        }
        assertEquals(exceptionMessage, "Invalid tag: Tag must match [a-zA-Z0-9][a-zA-Z0-9_.-]*");
    }
}
