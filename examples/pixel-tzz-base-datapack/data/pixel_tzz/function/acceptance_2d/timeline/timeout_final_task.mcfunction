# timeout_only path: the only result is submitted by this authoritative timeout callback.
$pixel_tzz task record_event $(task_instance_id) deadline_reached
$pixel_tzz task submit_result $(task_instance_id) finished
