
def call(java.util.ArrayList listOfResources, matlablic, matlabHSPro=true, UseNFS=false) {
    assert listOfResources instanceof java.util.List

    args = ['--privileged']

    for (i = 0; i < listOfResources.size(); i++) {
        if (listOfResources[i].equalsIgnoreCase( 'MATLAB' )) {
            echo '----Adding MATLAB Resources----'
            if (UseNFS) {
                args.add('-v "/nfs/apps/MATLAB":"/usr/local/MATLAB":ro')
                args.add('-v "/nfs/apps/resources/dot_matlab":"/root/.matlabro":ro')
                if (matlabHSPro)
                    args.add('-v "/nfs/apps/resources/mlhsp":"/mlhsp":ro')
                else
                    args.add('-v "/nfs/apps/resources/mlhsp":"/mlhspro":ro')
            }
            else {
                    args.add('-v "/usr/local/MATLAB":"/usr/local/MATLAB":ro')
                    args.add('-v "/root/.matlab":"/root/.matlabro":ro')
                    if (matlabHSPro)
                        args.add('-v "/mlhsp":"/mlhsp":ro')
                    else
                        args.add('-v "/mlhsp":"/mlhspro":ro')
            }
            // Add correct MAC to licenses work in Docker
            if (matlablic.equalsIgnoreCase( 'machine' )) {
                withCredentials([string(credentialsId: 'MAC_ADDR', variable: 'MAC_ADDR')]) {
                    args.add('--mac-address ' + MAC_ADDR)
                }
            }
        }
        else if (listOfResources[i].equalsIgnoreCase( 'Vivado' )) {
            echo '----Adding Vivado Resources----'
            if (UseNFS) {
                args.add('-v "/nfs/apps/Xilinx":"/opt/Xilinx":ro')
                args.add('-v "/nfs/apps/resources/dot_Xilinx":"/root/.Xilinxro":ro')
            }
        else {
                args.add('-v "/opt/Xilinx":"/opt/Xilinx":ro')
                args.add('-v "/root/.Xilinx":"/root/.Xilinxro":ro')
        }
            args.add('-e "LM_LICENSE_FILE=$LM_LICENSE_FILE" -e "XILINXD_LICENSE_FILE=$XILINXD_LICENSE_FILE"')
            // Zombie processed get created without this argument
            // https://stackoverflow.com/questions/55733058/vivado-synthesis-hangs-in-docker-container-spawned-by-jenkins
            args.add('--init')
        }
        else if (listOfResources[i].equalsIgnoreCase( 'images' )) {
            echo '----Adding Time of Flight Resources----'
            args.add('-v /usr/images:/images')
        }
        else if (listOfResources[i].equalsIgnoreCase( 'udev' )) {
            echo '----Adding udev resources of the host----'
            args.add('-v /etc/udev/rules.d:/etc/udev/rules.d')
            args.add('-v /run/udev/data:/run/udev/data')
        }
        else if (listOfResources[i].equalsIgnoreCase( 'x11forwarding' )) {
            echo '----Adding X11 Forwarding ----'
            args.add('-v /tmp/.X11-unix:/tmp/.X11-unix:rw')
            args.add('-v /home/analog/.Xauthority:/root/.Xauthority')
            args.add('-v /home/analog/.ssh:/root/.ssh')
        }
        else {
            args.add(listOfResources[i])
        }
    }

    return args
}
