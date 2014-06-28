package org.jenkinsci.backend.cifiller;

import hudson.cli.CLI;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;

/**
 * Puts greeting comments to pull requests.
 *
 */
public class App {
    CLI cli;

    public static void main(String[] args) throws Exception {
        new App().run();
    }

    public App() throws Exception {
        cli = new CLI(new URL("https://jenkins.ci.cloudbees.com/"));
    }

    public void authenticate() throws IOException, GeneralSecurityException {
        cli.authenticate(CLI.loadKey(new File(System.getProperty("user.home") + "/.ssh/id_rsa")));
    }

    public void close() throws IOException, InterruptedException {
        cli.close();
    }

    public void run() throws Exception {
        try {
            authenticate();
            ensureAll();
        } finally {
            close();
        }
    }

    public void ensureAll() throws IOException {
        GitHub gh = GitHub.connect();
        GHOrganization org = gh.getOrganization("jenkinsci");
        for (GHRepository r : org.listRepositories()) {
            ensure(r);
        }
    }

    /**
     * Ensures that the
     */
    public void ensure(GHRepository r) throws IOException {
        if (!isPluginRepository(r))
            return;

        String jobName = "plugins/" + r.getName();

        if (cli.execute(Arrays.asList("get-job", jobName),
                new NullInputStream(0),new NullOutputStream(),new NullOutputStream())==0) {
            System.out.printf("exists: %s\n",r.getName());

            if (!hasHook(r))
                createHook(r);

            return; // this job already exists
        }


        System.out.printf("create: %s\n",r.getName());

        String xml = IOUtils.toString(App.class.getResourceAsStream("job.xml"),"UTF-8");
        xml = xml.replaceAll("@@NAME@@",r.getName());

        if (cli.execute(Arrays.asList("create-job",jobName),new ByteArrayInputStream(xml.getBytes("UTF-8")),
                System.out,System.err)!=0) {
            throw new Error("Job creation failed");
        }
        createHook(r);
        
        // do an initial build of the new job
        if (cli.execute(Arrays.asList("build",jobName),
                new NullInputStream(0),new NullOutputStream(),new NullOutputStream())==0) {
            throw new Error("Initial build failed");
        }
    }

    private boolean isPluginRepository(GHRepository r) throws IOException {
        // first, quick tests based on names
        if (r.getName().endsWith("-plugin"))
            return true;
        if (r.getName().startsWith("backend-"))
            return false;
        if (r.getName().startsWith("lib-"))
            return false;


        // if name isn't conclusive, try to infer from pom.xml packaging
        try {
            String content = r.getFileContent("pom.xml").getContent();
            if (content.contains("<packaging>hpi</packaging>"))
                return true;
        } catch (FileNotFoundException e) {
            // if there's no pom.xml, this is not a plugin
            return false;
        }

        return false;
    }

    private boolean hasHook(GHRepository r) throws IOException {
        for (GHHook h : r.getHooks()) {
            if (h.getName().equals("jenkins"))
                return true;
        }
        return false;
    }

    private void createHook(GHRepository r) throws IOException {
        r.createHook("jenkins", Collections.singletonMap("jenkins_hook_url", "https://jenkins.ci.cloudbees.com/github-webhook/"),null,true);
    }
}
