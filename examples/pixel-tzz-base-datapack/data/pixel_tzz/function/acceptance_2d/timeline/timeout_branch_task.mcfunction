# early_or_timeout timeout path: record the settling event, then submit the registered timeout result.
$pixel_tzz task record_event $(task_instance_id) deadline_reached
$pixel_tzz task submit_result $(task_instance_id) timeout
