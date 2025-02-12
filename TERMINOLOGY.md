* Actor - A remote entity (e.g. user/bot, webhook) that is used for *acting* on remote side
* Puppet - A *local* matrix account managed by AS that represents remote users
* Actor account - The account (as in authorization) which actor uses to access its entity on remote side
* Bridge bypass - usage of actor account in bypass of bridge, e.g. logging with it and doing actions manually
* Actor account puppet - A puppet that represents events bypassed bridge. It is a "byproduct" puppet that is normally
  created if outbound events aren't ignored but with careful filtering it can be used to replicate bypassed events.