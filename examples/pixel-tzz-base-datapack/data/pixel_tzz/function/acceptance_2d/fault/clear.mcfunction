# Restore the fault gate before asking the host UI to retry the failed callback.
data modify storage pixel_tzz:acceptance_2d session.fault.command set value "return 1"
