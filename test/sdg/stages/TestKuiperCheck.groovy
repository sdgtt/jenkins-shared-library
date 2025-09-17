package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestKuiperCheck extends Specification {

    def shell
    def getGauntEnv

    IStepExecutor steps
    IContext context

    def setup() {
        // mock the context
        shell = new GroovyShell()
        getGauntEnv = shell.parse(new File('vars/getGauntEnv.groovy'))

        steps = Mock(IStepExecutor.class)
        context = Mock(IContext.class)
    }

    def "test getStageName"() {
        given:
        def kuiperCheck = new KuiperCheck()

        expect:
        kuiperCheck.getStageName() == "KuiperCheck"
    }

    def "test getCls"() {
        given:
        def kuiperCheck = new KuiperCheck()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = kuiperCheck.getCls()

        then:
        closure instanceof Closure
    }

    def "test stageSteps - KPBC run"() {
        given:
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        // Mock gauntlet
        context.getStepExecutor() >> steps
        context.isDefault() >> false

        // Create gauntEnv with required properties
        def mockGauntEnv = getGauntEnv.call("NA", "NA", "NA", "v0.31", "NA")
        mockGauntEnv.kuiper_checker_branch = "master"
        mockGauntEnv.kuiper_checker_repo = "https://github.com/sdgtt/kuiper-post-build-checker.git"

        steps.getGauntEnv(_, _, _, _, _) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'

        // Mock all shell commands that will be called
        steps.sh(_) >> { args ->
            if (args instanceof Map) {
                if (args.returnStdout == true && args.script?.contains('nebula update-config')) {
                    return "192.168.1.100"  // Return IP for nebula commands
                } else if (args.returnStatus == true) {
                    return 0  // Return success status code for pytest
                }
            }
            return 0  // Default return
        }

        // Mock dir operations with closure execution
        steps.dir(_, _) >> { String dirName, Closure closure ->
            closure.call()
        }

        // Mock other Jenkins pipeline steps
        steps.fileExists(_) >> true
        steps.junit({ Map params -> params.testResults && params.allowEmptyResults != null }) >> null
        steps.publishHTML(_) >> null
        steps.stage(_, _) >> { String stageName, Closure closure ->
            closure.call()
        }

        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA", "NA", "NA", "v0.31", "NA")

        // Ensure gauntlet has the correct gauntEnv
        gauntlet.gauntEnv = mockGauntEnv

        // Mock the logger
        def mockLogger = Mock(sdg.Logger)
        gauntlet.logger = mockLogger

        // Mock gauntlet methods using metaClass
        gauntlet.metaClass.run_i = { String cmd, boolean doRetry = false ->
            return null  // Mock successful execution
        }
        gauntlet.metaClass.nebula = { String cmd ->
            return "192.168.1.100"  // Mock IP address return
        }

        def KuiperCheck = new KuiperCheck()

        when:
        def exceptionThrown = false
        try {
            KuiperCheck.stageSteps(gauntlet, board)
        } catch (Exception e) {
            exceptionThrown = true
            // We expect a NominalException due to status code, that's okay for this test
        }

        then:
        // Verify the logger was called
        1 * mockLogger.info('Running KuiperCheck for zynq-zc702-adv7511-ad9361-fmcomms2-3')
        
        // Verify the pytest command was executed with the expected structure
        1 * steps.sh({ Map params -> 
            params.script && 
            params.script.contains('python3 -m pytest') &&
            params.script.contains('--html=testhtml/') &&
            params.script.contains('--junitxml=testxml/') &&
            params.script.contains('--ip=') &&
            params.script.contains('not hardware_check') &&
            params.returnStatus == true
        })
        
        exceptionThrown == true  // We expect an exception due to pytest failure
    }
}