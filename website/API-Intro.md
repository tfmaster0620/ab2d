# AB2D Sandbox

This site is a developer's guide on how to use the AB2D API to bulk search select Part A
and Part B claim data by Medicare Part D plan (PDP) sponsors. The AB2D API is a RESTful based 
web service that allows PDP sponsors to retrieve Part A and Part B claim data. This service 
provides asynchronous access to the data. The sponsor will request data, a job will be created to
retrieve this data, a job number returned, the job will be processed and once it is complete, the 
data will be available to download. The status of the job can be requested at any time to determine
whether it is complete.

Data will be limited to a subset of explanation of benefit data records by the following constraints:
- Only claim data belonging to the PDP sponsors subscriber's list
- Only data from subscribers who did not opt out of data sharing by calling 1-(800)-Medicare 
(1-800-633-4227)
- Only Part A and Part B data. Part D data is excluded.
- Only data specified by the Secretary of Health and Human Services within the explanation of benefit 
object (not all data in the explanation of benefit object is included in the returned object)

### Data Use and Limitations
This data may be used for:
- Optimizing therapeutic outcomes through improved medication use
- Improving care coordination so as to prevent adverse healthcare outcomes, such as
preventable emergency department visits and hospital readmissions
- For any other purposes determined appropriate by the Secretary

The sponsors may not use the data:
- To inform coverage determination under Part D
- To conduct retroactive reviews of medically accepted conditions
- To facilitate enrollment changes to a different or a MA-PD plan offered b the same parent
organization
- To inform marketing of benefits
- For any other purpose the Secretary determines is necessary to include in order to protect the
identity of individuals entitled to or enrolled in Medicare, and to protect the security of
personal health information.

### Access
To get access to the sandbox environment ...
More detailed information is available <a href="API.md">here</a>.

### Legislation
<a href=https://www.congress.gov/bill/115th-congress/house-bill/1892/text>Bipartisan Budget Act of 2018</a><br>
<a href="https://www.federalregister.gov/documents/2019/04/16/2019-06822/medicare-and-medicaid-programs-policy-and-technical-changes-to-the-medicare-advantage-medicare">
Final Rule</a>


### Notes
<a href="https://hapifhir.io/">HAPI FHIR</a> was used to implement the HL7 FHIR standard.


