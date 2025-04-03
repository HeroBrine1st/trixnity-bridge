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
* Provision of user/room - an act of copying the room from remote side to matrix side.
    * Historic provision - a provision from historically accurate information (e.g. from backfilling or in real time
      as entity is created on remote side).
    * Automatic provision - a provision from current state of the entity, usually done due to inability of remote worker
      to do proper historic provision.  
      In other words, this means room or user provision when remote worker did not explicitly request that, so room/user
      is provisioned because otherwise sending event is impossible.