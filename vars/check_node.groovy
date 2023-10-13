def get_agents(){
    def jenkins = jenkins.model.Jenkins.get()
    def online_agents = []
    
    for (computer in jenkins.getComputers()) {
        if (!computer.offline) {
            online_agents.add(computer.getDisplayName())
        }
    }
    println(online_agents)
    return online_agents
}

def call(agentName){
    retries = 10
    sh 'vagrant winrm --command \"Get-Service -Name "JenkinsAgent"\"'
    for(int i = 0;i<retries;i++) {
        
        agents = get_agents()
        for(agent in agents){
            if(agent.contains(agentName)){
                println("FOUND "+agent)
                return agent
            }
        }
        println("Agent not up yet... waiting");
        sleep 5
    }
    println("ERROR: Agent not found. Trying to display logs")
    sh 'vagrant winrm --command "dir C:\\jenkins"'
    sh 'vagrant winrm --command "dir C:\\jenkins\\log"'
    sh 'vagrant winrm --command "type C:\\jenkins\\log\\*.log"'
    throw new Exception("Agent not found")
}
