* Actor - A remote entity (e.g. user/bot, webhook) that is used for *acting* on remote side
* Personal actor - An actor that is intended to be used by human, as opposed to bots.
* Puppet - A *local* matrix account managed by AS that represents remote users
* Actor account - The account (as in authorization) which actor uses to access its entity on remote side
* Bridge bypass - usage of actor account in bypass of bridge, e.g. logging with it and doing actions manually. Usually
  only personal actors are susceptible to that.
* Actor account puppet - A puppet that represents events bypassed bridge. It is a "byproduct" puppet that is normally
  created if outbound events aren't ignored but with careful filtering it can be used to replicate bypassed events.
* Personal bridge - a bridge with support of personal actors. Such bridge allows a person to bridge its own account to
  matrix. Also known as single-puppeted
  bridge [according to old matrix doc](https://matrix.org/docs/older/types-of-bridging/)