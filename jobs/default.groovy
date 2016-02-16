import static com.base2.ciinabox.JobHelper.*

if(folder != null) {
  folder("$folder") {
    description("$folder")
  }
  jobName = "$folder/$jobName"
}
def job = job(jobName)
defaults(job,jm.getParameters())
