package net.bcharris.fixedincomepricing;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.util.Arrays;
import org.codehaus.groovy.control.CompilationFailedException;

/**
 * Generates values for each period from a Groovy script.
 */
public class GroovyPeriodValueGenerator {

    public static class GenerationException extends Exception {

        public GenerationException(String message) {
            super(message);
        }
    }
    private final Script script;
    private final Double constantValue;

    public GroovyPeriodValueGenerator(String groovyScript) throws CompilationFailedException {
        String candidate = groovyScript.trim();
        if (candidate.startsWith("return ")) {
            candidate = candidate.substring(7).trim();
        }
        Double constant;
        try {
            constant = Double.parseDouble(candidate);
        } catch (NumberFormatException ex) {
            constant = null;
        }
        if (constant == null) {
            this.constantValue = null;
            this.script = new GroovyShell().parse(groovyScript);
        } else {
            this.constantValue = constant;
            this.script = null;
        }
    }

    /**
     * Generate values for a number of periods.
     * @param values The array in which to store the generated values.
     * @throws GenerationException If there is a problem with the script.
     */
    public void generate(double[] values) throws GenerationException {
        if (constantValue != null) {
            Arrays.fill(values, constantValue);
        } else {
            Binding binding = new Binding();
            script.setBinding(binding);
            for (int i = 0; i < values.length; i++) {
                binding.setVariable("period", new Integer(i));
                binding.setVariable("periods", values.length);
                Object result;
                try {
                    result = script.run();
                } catch (Exception ex) {
                    throw new GenerationException("There was a problem with the Groovy script.  " + ex.getMessage());
                }
                if (result == null) {
                    throw new GenerationException("The script did not return a value.");
                }
                try {
                    values[i] = Double.valueOf(result.toString());
                } catch (NumberFormatException ex) {
                    throw new GenerationException("The script did not return a valid floating point number.");
                }
            }
        }
    }
}
