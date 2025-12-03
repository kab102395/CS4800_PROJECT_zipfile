components {
  id: "platform"
  component: "/main/runner_stan-main/level/objects/desk_platform.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/level/level.atlas\"\n"
  "}\n"
  ""
}
embedded_components {
  id: "collisionobject"
  type: "collisionobject"
  data: "type: COLLISION_OBJECT_TYPE_KINEMATIC\n"
  "mass: 0.0\n"
  "friction: 0.1\n"
  "restitution: 0.5\n"
  "group: \"geometry\"\n"
  "mask: \"hero\"\n"
  "embedded_collision_shape {\n"
  "  shapes {\n"
  "    shape_type: TYPE_BOX\n"
  "    position {\n"
  "      x: 196.0\n"
  "      y: 1.0\n"
  "    }\n"
  "    rotation {\n"
  "    }\n"
  "    index: 0\n"
  "    count: 3\n"
  "    id: \"Box\"\n"
  "  }\n"
  "  data: 391.81525\n"
  "  data: 81.13532\n"
  "  data: 10.0\n"
  "}\n"
  ""
}
embedded_components {
  id: "sprite1"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "size {\n"
  "  x: 391.0\n"
  "  y: 153.0\n"
  "}\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/level/level.atlas\"\n"
  "}\n"
  ""
  position {
    x: 393.0
    y: 1.0
  }
}
embedded_components {
  id: "extra_credit_factory"
  type: "factory"
  data: "prototype: \"/main/runner_stan-main/level/objects/extra_credit.go\"\n"
  ""
}
