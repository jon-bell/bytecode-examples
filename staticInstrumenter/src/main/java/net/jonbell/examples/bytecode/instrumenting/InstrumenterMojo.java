package net.jonbell.examples.bytecode.instrumenting;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "instrumentBytecode", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class InstrumenterMojo extends AbstractMojo {

	@Component
	private MavenProject project;
	@Parameter(defaultValue = "false", readonly = true)
	private boolean instrumentTest;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		String buildOutput = null;
		if(instrumentTest)
			buildOutput = project.getBuild().getTestOutputDirectory();
		else
			buildOutput = project.getBuild().getOutputDirectory();
		
		if (buildOutput.endsWith("/"))
			buildOutput = buildOutput.substring(0, buildOutput.length() - 1);
		Instrumenter.main(new String[] { buildOutput, buildOutput + "-instrumented" });

	}
}
