import com.cloudbees.groovy.cps.NonCPS

/////////////////////////////////////////////////////
/*
Map matrix_axes = [
    DOCKERIMAGE: ["ubuntu:18.04", "ubuntu:19.10", "ubuntu:20.04"],
    LIBIIOBRANCHES: ["v0.18","v0.19","master"],
    DEPENDENCYFUNC: ["ubuntu","fedora"]
]
*/

@NonCPS
List getMatrixAxes(Map matrix_axes) {
    List axes = []
    matrix_axes.each { axis, values ->
        List axisList = []
        values.each { value ->
            axisList << [(axis): value]
        }
        axes << axisList
    }
    // calculate cartesian product
    axes.combinations()*.sum()
}

List genMatrixDockerBuilds(Map matrix_axes, String agent_name) {
    List axes = getMatrixAxes(matrix_axes).findAll { axis ->
        (axis['DOCKERIMAGE'].contains('ubuntu') && axis['DEPENDENCYFUNC'] == 'ubuntu')
    }

    def branches = [:]
    for ( i = 0; i < axes.size(); i++ ) {
        Map axis = axes[i]
        // Enumerate variables setters
        List axisEnv = axis.collect { k, v ->
            "${k}=${v}"
        }
        // Split out top level variables
        def dimage = axis['DOCKERIMAGE']
        def libiiobranch = axis['LIBIIOBRANCHES']
        String branchName = "os:${axis['DOCKERIMAGE']} && libiio:${axis['LIBIIOBRANCHES']}"

        branches[branchName] = {
            node (agent_name) {
                withEnv(axisEnv) {
                    docker.image(dimage).inside {
                        sh 'DEBIAN_FRONTEND="noninteractive" apt-get -qq update'
                        sh 'rm -rf pyadi-iio || true'
                        sh 'git clone https://github.com/analogdevicesinc/pyadi-iio.git'
                    }
                }
            }
        }
    }
}
