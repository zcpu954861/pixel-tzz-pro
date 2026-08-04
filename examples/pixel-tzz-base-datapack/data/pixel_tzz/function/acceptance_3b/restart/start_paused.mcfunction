# restart=continue 验收：在短演出完成前自动暂停，暂停后可从容保存退出。
pixel_tzz_pro message play pixel_tzz:acceptance/policy_matrix to PlayerB with storage pixel_tzz:acceptance_3b call
schedule function pixel_tzz:acceptance_3b/restart/pause 10t replace
