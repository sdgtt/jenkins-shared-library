package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestRecoverBoard extends Specification {

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
        RecoverBoard ubf_stage = new RecoverBoard()

        expect:
        ubf_stage.getStageName() == "RecoverBoard"
    }

    def "test getCls"() {
        given:
        RecoverBoard _stage = new RecoverBoard()
        String board = "pluto"

        when:
        def closure = _stage.getCls()

        then:
        closure instanceof Closure
    }

    def "test stageSteps for pluto"() {
        given:

        //Mock gauntlet 
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

        RecoverBoard _stage = new RecoverBoard()
        String board = "pluto"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        when:
        _stage.stageSteps(gauntlet, board)


        then:
        1 * steps.echo('[INFO] Running RecoverBoard for ' + board)
        1 * steps.echo('[WARNING] Recover stage does not support pluto yet!')
    }

    def "test stageSteps for non-pluto"() {
        given:

        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","NA","artifactory")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","artifactory")

        RecoverBoard _stage = new RecoverBoard()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        when:
        _stage.stageSteps(gauntlet, board)


        then:
        1 * steps.echo('[INFO] Running RecoverBoard for ' + board)
        1 * steps.echo('[INFO] Fetching reference boot files')
        1 * steps.sh(
            'set -o pipefail; nebula show-log dl.bootfiles --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 ' +
            '--source-root="/var/lib/tftpboot" --source=artifactory --branch="release" --filetype="boot_partition" ' +
            '2>&1 | tee out.out'
        )
        1 * steps.echo('[INFO] Extracting reference fsbl and u-boot')
        1 * steps.echo('[INFO] Executing board recovery...')
        1 * steps.sh([
            script: 'nebula manager.recovery-device-manager --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 ' +
                    '--folder=outs --sdcard', 
            returnStdout: true
        ])
    }

    def "test stageSteps for non-pluto with exception"() {
        given:

        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","NA","artifactory")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","artifactory")

        RecoverBoard _stage = new RecoverBoard()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)
        gauntlet.set_env("env", [JOB_NAME: "test", BUILD_NUMBER: "1"])

        // trigger an exception
        steps.sh("mkdir -p recovery; mv outs recovery") >> { throw new Exception() }
        

        when:
        _stage.stageSteps(gauntlet, board)


        then:
        1 * steps.sh([
            script: 'nebula netbox.disable-board --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 ' +
                    '--failure --reason="Disabled by test 1" --power-off',
            returnStdout: true
        ])
        thrown Exception
    }

}
