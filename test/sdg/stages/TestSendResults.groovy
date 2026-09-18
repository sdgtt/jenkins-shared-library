package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestSendResults extends Specification {
    def shell
    def getGauntEnv

    IStepExecutor steps
    IContext context

    def setup() {
        // mock the context
        shell = new GroovyShell()
        getGauntEnv = shell.parse(new File('vars/getGauntEnv.groovy'))
        
        steps =  Mock(IStepExecutor.class)
        context = Mock(IContext.class)
    }

    def "test getStageName"() {
        given:
        def SendResults = new SendResults()

        expect:
        SendResults.getStageName() == "SendResults"
    }

    def "test getCls"() {
        given:
        def SendResults = new SendResults()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = SendResults.getCls()

        then:
        closure instanceof Closure
    }

    def "test stageSteps - send results"() {
        given:
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")

        def SendResults = new SendResults()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        steps.getEnv() >> [BUILD_NUMBER: '456', JENKINS_HOME: '/mocked/path']
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        when:
        SendResults.stageSteps(gauntlet, board)
        
        then:   
        1 * steps.echo("[INFO] Starting send log to elastic search")
        1 * steps.sh(['script':'telemetry log-boot-logs boot_folder_name zynq-zc702-adv7511-ad9361-fmcomms2-3 hdl_hash \'NA\' linux_hash \'NA\' boot_partition_hash \'null\' hdl_branch NA linux_branch NA boot_partition_branch NA is_hdl_release False is_linux_release False is_boot_partition_release False uboot_reached False linux_prompt_reached False drivers_enumerated 0 drivers_missing 0 dmesg_warnings_found 0 dmesg_errors_found 0 jenkins_build_number 456 jenkins_project_name \'null\' jenkins_agent null jenkins_trigger manual pytest_errors 0 pytest_failures 0 pytest_skipped 0 pytest_tests 0 matlab_errors 0 matlab_failures 0 matlab_skipped 0 matlab_tests 0 last_failing_stage NA last_failing_stage_failure NA', 'returnStdout':true])

    }
}

    