package org.jenkinsci.plugins.gcriologin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class GcrLoginBuildWrapper extends BuildWrapper {

    private final static String REGISTRY = "https://gcr.io";

    private final static String LOGIN_CMD =
            "/bin/bash -c 'METADATA=http://metadata.google.internal./computeMetadata/v1 && " +
            "SVC_ACCT=$METADATA/instance/service-accounts/default && " +
            "ACCESS_TOKEN=$(curl -H \\'Metadata-Flavor: Google\\' $SVC_ACCT/token | cut -d\\'\"\\' -f 4) && " +
            "docker login -e not@val.id -u _token -p $ACCESS_TOKEN " + REGISTRY + "'";

    public static final String DOCKERCFG = ".dockercfg";

    @DataBoundConstructor
    public GcrLoginBuildWrapper() {}

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final int setUpRetCode = launcher.launch()
                .cmdAsSingleString(LOGIN_CMD)
				.stdout(listener.getLogger())
				.join();

        return setUpRetCode == 0 ? new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                return logout(launcher, listener);
            }
        } : null;
    }

    private boolean logout(Launcher launcher, BuildListener listener) {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String,Object> userData;

        final EnvVars envVars;
        try {
            envVars = Computer.currentComputer().getEnvironment();
        } catch (IOException e) {
            listener.fatalError("Could not retrieve slave environment.");
            return false;
        } catch (InterruptedException e) {
            listener.fatalError("Could not retrieve slave environment.");
            return false;
        }

        final FilePath dockerCfg = new FilePath(launcher.getChannel(), envVars.get("HOME") + "/" + DOCKERCFG);
        try {
            userData = mapper.readValue(dockerCfg.readToString(), Map.class);
        } catch (IOException e) {
            listener.fatalError("Could not read and parse .dockercfg.");
            return false;
        } catch (InterruptedException e) {
            listener.fatalError("Could not read .dockercfg.");
            return false;
        }

        userData.remove(REGISTRY);

        try {
            dockerCfg.write(mapper.writeValueAsString(userData), "UTF-8");
        } catch (JsonProcessingException e) {
            listener.fatalError("Could not write .dockercfg");
            return false;
        } catch (InterruptedException e) {
            listener.fatalError("Could not write .dockercfg");
            return false;
        } catch (IOException e) {
            listener.fatalError("Could not retrieve slave environment.");
            return false;
        }
        return true;
    }

    @Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		public DescriptorImpl() {
			load();
		}

		@Override
		public String getDisplayName() {
			return "gcr.io Login";
		}

		@Override
		public boolean isApplicable(AbstractProject item) {
			return true;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			save();
			return super.configure(req, formData);
		}
	}
}
