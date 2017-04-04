import static com.base2.ciinabox.JobHelper.*

if(folder != null && folder != "") {
  folder("$folder") {
    description("$folder")
  }
  jobName = "$folder/$jobName"
} else {
  jobName = "$jobName"
}
def job = pipelineJob(jobName)
defaults(job,jm.getParameters())
