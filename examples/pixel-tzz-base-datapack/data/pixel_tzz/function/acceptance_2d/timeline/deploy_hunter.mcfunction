# Runs once as each online frozen hunter and proves the locked exclusive field reached the task callback.
$data modify storage pixel_tzz:acceptance_2d session.current.deployments append value {task_instance_id:"$(task_instance_id)",player_uuid:"$(player_uuid)",player_name:"$(player_name)",player_field:$(player_field)}
