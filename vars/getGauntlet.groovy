def call(hdlBranch="NA", linuxBranch="NA", bootPartitionBranch="release",firmwareVersion="NA", bootfile_source="artifactory", rpi_branch="NA") {
    def harness =  new sdg.Gauntlet()
    harness.construct(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source, rpi_branch)
    return harness
}
