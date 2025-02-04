package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestUpdateBOOTFiles extends Specification {

    def shell
    def getGauntEnv

    IStepExecutor steps
    IContext context

    def setup() {
        // mock the context
        shell = new GroovyShell()
        getGauntEnv = shell.parse(new File('vars/getGauntEnv.groovy'))
        
        steps =  Mock(IStepExecutor.class);
        context = Mock(IContext.class);
    }

    def "test getStageName"() {
        given:
        UpdateBOOTFiles ubf_stage = new UpdateBOOTFiles()

        expect:
        ubf_stage.getStageName() == "UpdateBOOTFiles"
    }

    def "test getCls"() {
        given:
        UpdateBOOTFiles ubf_stage = new UpdateBOOTFiles()
        String board = "pluto"
        def closure = ubf_stage.getCls()

        when:
        closure = ubf_stage.getCls()

        then:
        closure instanceof Closure
    }

    def "test stageSteps for pluto"() {
        given:

        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        context.getStepExecutor().getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","v0.31","NA")
        context.getStepExecutor().isUnix() >> true
        context.getStepExecutor().sh(script: 'uname', returnStdout: true) >> 'Linux'
        context.getStepExecutor().fileExists('out.out') >> true
        context.getStepExecutor().readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")

        UpdateBOOTFiles ubf_stage = new UpdateBOOTFiles()
        String board = "pluto"
        String ml_bootbin_case = "NA"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        when:
        ubf_stage.stageSteps(gauntlet, board, ml_bootbin_case)

        then:
        1 * steps.echo('[INFO] Running UpdateBOOTFiles for '+ board)
        1 * steps.echo('[INFO] Board name passed: ' + board)
        1 * steps.echo("[INFO] Branch: " + gauntlet.get_env("branches").toString())
        1 * steps.sh('set -o pipefail; nebula show-log dl.bootfiles --board-name=pluto --source="github" --branch="v0.31" --filetype="firmware" 2>&1 | tee out.out')
        1 * steps.sh('set -o pipefail; nebula show-log manager.update-boot-files --board-name=pluto --folder=outs 2>&1 | tee out.out')
        1 * steps.retry(2,_)
        1 * steps.retry(1,_)
        1 * steps.sh('set -o pipefail; nebula show-log manager.verify-checksum --board-name=pluto --folder=outs 2>&1 | tee out.out')
        1 * steps.archiveArtifacts(artifacts: 'uart_boot_*.log', followSymlinks: false, allowEmptyArchive: true)

        assert gauntlet.get_env("elastic_logs")[board]["uboot_reached"] == "True"
        assert gauntlet.get_env("elastic_logs")[board]["kernel_started"] == "True"
        assert gauntlet.get_env("elastic_logs")[board]["linux_prompt_reached"] == "True"
        assert gauntlet.get_env("elastic_logs")[board]["post_boot_failure"] == "False"        
    }

    def "test stageSteps for non-pluto"() {
        
        given:
        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        context.getStepExecutor().getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","release","NA","artifactory")
        context.getStepExecutor().isUnix() >> true
        context.getStepExecutor().sh(script: 'uname', returnStdout: true) >> 'Linux'
        context.getStepExecutor().fileExists('out.out') >> true
        context.getStepExecutor().readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")

        UpdateBOOTFiles ubf_stage = new UpdateBOOTFiles()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        String ml_bootbin_case = "NA"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)
        def dl_cmd = 'dl.bootfiles --board-name=' + board + 
                 ' --source-root=/var/lib/tftpboot' + 
                 ' --source=artifactory' + 
                 ' --branch=release' + 
                 ' --filetype="boot_partition"'

        when:
        ubf_stage.stageSteps(gauntlet, board, ml_bootbin_case)

        then:
        1 * steps.echo('[INFO] Running UpdateBOOTFiles for '+ board)
        1 * steps.echo('[INFO] Board name passed: ' + board)
        1 * steps.echo("[INFO] Branch: " + gauntlet.get_env("branches").toString())
        1 * steps.sh('set -o pipefail; nebula show-log '+ dl_cmd + ' 2>&1 | tee out.out')
        1 * steps.sh('set -o pipefail; nebula show-log manager.update-boot-files --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 --folder=outs 2>&1 | tee out.out')
        1 * steps.sh('set -o pipefail; nebula show-log manager.verify-checksum --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 --folder=outs 2>&1 | tee out.out')
        1 * steps.archiveArtifacts(artifacts: 'uart_boot_*.log', followSymlinks: false, allowEmptyArchive: true) 

        assert gauntlet.get_env("elastic_logs")[board]["uboot_reached"] == "True"
        assert gauntlet.get_env("elastic_logs")[board]["kernel_started"] == "True"
        assert gauntlet.get_env("elastic_logs")[board]["linux_prompt_reached"] == "True"
        assert gauntlet.get_env("elastic_logs")[board]["post_boot_failure"] == "False" 
    }
}




