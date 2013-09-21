package org.jenkinsci.backend.cifiller;

import hudson.cli.CLI;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Puts greeting comments to pull requests.
 *
 */
public class App {
    CLI cli;

    public static void main(String[] args) throws Exception {
        new App().run();
    }

    public void run() throws Exception {
        cli = new CLI(new URL("https://jenkins.ci.cloudbees.com/"));
        cli.authenticate(CLI.loadKey(new File(System.getProperty("user.home")+"/.ssh/id_rsa")));

        GitHub gh = GitHub.connect();
        GHOrganization org = gh.getOrganization("jenkinsci");
        for (GHRepository r : org.listRepositories()) {
            // as a roll out, only do this for 10% of the repositories
            if (r.getName().endsWith("-plugin"))
                ensure(r);
        }
    }

    protected void ensure(GHRepository r) throws IOException {
        String jobName = "plugins/" + r.getName();

        if (cli.execute(Arrays.asList("get-job", jobName),
                new NullInputStream(0),new NullOutputStream(),new NullOutputStream())==0) {
            System.out.printf("exists: %s\n",r.getName());
            return; // this job already exists
        }


        System.out.printf("create: %s\n",r.getName());

        String xml = IOUtils.toString(App.class.getResourceAsStream("job.xml"),"UTF-8");
        xml = xml.replaceAll("@@NAME@@",r.getName());

        if (cli.execute(Arrays.asList("create-job",jobName),new ByteArrayInputStream(xml.getBytes("UTF-8")),
                System.out,System.err)!=0) {
            throw new Error("Job creation failed");
        }
        r.createHook("jenkins", Collections.singletonMap("jenkins_hook_url", "https://jenkins.ci.cloudbees.com/github-webhook/"),null,true);
    }
}
