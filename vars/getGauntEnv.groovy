/**
 * Contruct a map of fields that will be used by gauntlet
 * @param hdlBranch - String of name of hdl branch to use for bootfile source
 * @param linuxBranch - String of name of linux branch to use for bootfile source
 * @param bootPartitionBranch - String of name of boot partition branch to use for bootfile source, set to 'NA' if hdl and linux is to be used
 * @param firmwareVersion - String of name of firmware version branch to use for pluto and m2k
 * @param bootfile_source - String location of bootfiles. Options: sftp, artifactory, http, local
 * @return map of initially populated field/value pairs
 */
private def call(hdlBranch, linuxBranch, bootPartitionBranch,firmwareVersion, bootfile_source) {
    return [
            hdlBranch: hdlBranch,
            linuxBranch: linuxBranch,
            bootPartitionBranch: bootPartitionBranch,
            branches: ( bootPartitionBranch == 'NA')? hdlBranch : bootPartitionBranch,
            filetype: ( bootPartitionBranch == 'NA')? ' --filetype="hdl_linux"' : ' --filetype="boot_partition"',
            firmwareVersion: firmwareVersion,
            bootfile_source: bootfile_source,
            job_trigger: 'manual',
            agents_online: '',
            debug: false,
            board_map: [:],
            stages: [],
            agents: [],
            boards: [],
            required_hardware: [],
            required_agent: [],
            firmware_boards: ['pluto','m2k'],
            enable_docker: false,
            docker_image: 'tfcollins/sw-ci:latest',
            docker_args: ['MATLAB','Vivado'],
            docker_host_mode: true,
            update_nebula_config: true,
            nebula_config_source: 'netbox',
            netbox_ip: '',
            netbox_port: '',
            netbox_base_url: '',
            netbox_token: '',
            netbox_devices_tag: '',
            netbox_nebula_template: 'template_gen.yaml',
            enable_update_boot_pre_docker: false,
            lock_agent: false,
            board_sub_categories : ['rx2tx2'],
            enable_resource_queuing: false,
            setup_called: false,
            nebula_debug: false,
            nebula_local_fs_source_root: '/var/lib/tftpboot',
            elastic_server: '',
            iio_uri_source: 'ip',
            iio_uri_baudrate: 921600,
            configure_called: false,
            pytest_libiio_repo: 'https://github.com/tfcollins/pytest-libiio.git',
            pytest_libiio_branch: 'master',
            pyadi_iio_repo: 'https://github.com/analogdevicesinc/pyadi-iio.git',
            pyadi_iio_branch: 'master',
            libad9361_iio_repo: 'https://github.com/analogdevicesinc/libad9361-iio.git',
            libad9361_iio_branch : 'master',
            nebula_repo: 'https://github.com/sdgtt/nebula.git',
            nebula_branch: 'master',
            libiio_repo: 'https://github.com/analogdevicesinc/libiio.git',
            libiio_branch: 'master',
            telemetry_repo: 'https://github.com/sdgtt/telemetry.git',
            telemetry_branch: 'master',
            matlab_release: 'R2021a',
            matlab_repo: 'https://github.com/analogdevicesinc/TransceiverToolbox.git',
            matlab_branch: 'master',
            matlab_commands: [],
            matlab_timeout: '10m',
            no_os_repo: 'https://github.com/analogdevicesinc/no-OS.git',
            no_os_branch: 'master',
            vivado_ver: '2019.1',
            nebula_config_repo: 'https://github.com/sdgtt/nebula-config.git',
            nebula_config_branch: 'master',
            send_results: false,
            elastic_logs : [:],
            log_jira: false,
            log_jira_stages: [],
            max_retry: 3,
            recovery_ref: "SD",
            log_artifacts: false,
            credentials_id: '',
            toolbox_generated_bootbin: false,
            ml_toolbox: '',
            ml_branch: '',
            ml_build: '',
            ml_test_stages: 0,
            internal_stages_to_skip: [:] // Number of stages to skip. Used for test skipping for MATLAB
    ]
}