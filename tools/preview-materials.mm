// macOS-only visual QA utility, not part of the Android runtime.
// Renders the actual shared surface GLSL and parses the Java palette (no copied asset colors).
// clang++ -std=c++17 -Wno-deprecated-declarations tools/preview-materials.mm \
//   -framework OpenGL -framework AppKit -o /private/tmp/voxelstrike-material-preview
// Run from the repository root with an absolute output PNG path.
#import <AppKit/AppKit.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#include <vector>
#include <cstdio>

static NSString *read(NSString *path) {
    NSError *error = nil;
    NSString *text = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:&error];
    if (!text) { NSLog(@"%@", error); exit(1); }
    return text;
}
static GLuint compile(GLenum type, NSString *source) {
    GLuint shader = glCreateShader(type);
    const char *text = source.UTF8String;
    glShaderSource(shader, 1, &text, nullptr); glCompileShader(shader);
    GLint ok; glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[8192]; glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        fprintf(stderr, "%s\n", log); exit(1); }
    return shader;
}
int main(int argc, char **argv) { @autoreleasepool {
    if (argc != 2 || argv[1][0] != '/') { fprintf(stderr, "Pass an absolute output PNG path.\n"); return 1; }
    NSString *java = read(@"app/src/main/java/com/ccs/voxelstrike/voxel/Materials.java");
    NSMutableDictionary *ids = [NSMutableDictionary dictionary];
    NSRegularExpression *constants = [NSRegularExpression regularExpressionWithPattern:
        @"public static final byte (\\w+) = (?:\\(byte\\) )?(\\d+);" options:0 error:nil];
    for (NSTextCheckingResult *r in [constants matchesInString:java options:0 range:NSMakeRange(0, java.length)]) {
        ids[[java substringWithRange:[r rangeAtIndex:1]]] = @([[java substringWithRange:[r rangeAtIndex:2]] intValue]);
    }
    std::vector<float> palette(96 * 8, 0);
    NSRegularExpression *definitions = [NSRegularExpression regularExpressionWithPattern:
        @"define\\((\\w+), ([^;]+)\\);" options:0 error:nil];
    for (NSTextCheckingResult *r in [definitions matchesInString:java options:0 range:NSMakeRange(0, java.length)]) {
        NSNumber *number = ids[[java substringWithRange:[r rangeAtIndex:1]]];
        if (!number) continue;
        int id = number.intValue, slot = id >= 200 ? 64 + id - 200 : id;
        NSArray *fields = [[java substringWithRange:[r rangeAtIndex:2]] componentsSeparatedByString:@","];
        if (fields.count != 6) return 2;
        float r0 = [fields[0] floatValue], g = [fields[1] floatValue], b = [fields[2] floatValue];
        float emission = [fields[5] floatValue];
        float values[8] = {r0, g, b, [fields[3] floatValue], r0 * emission, g * emission,
            b * emission, [fields[4] floatValue]};
        for (int i = 0; i < 8; ++i) palette[slot * 8 + i] = values[i];
    }
    CGLPixelFormatAttribute attributes[] = {kCGLPFAOpenGLProfile,
        (CGLPixelFormatAttribute) kCGLOGLPVersion_3_2_Core, (CGLPixelFormatAttribute) 0};
    CGLPixelFormatObj format; GLint count; CGLContextObj context;
    if (CGLChoosePixelFormat(attributes, &format, &count) != kCGLNoError) return 3;
    if (CGLCreateContext(format, nullptr, &context) != kCGLNoError) return 3;
    CGLDestroyPixelFormat(format); CGLSetCurrentContext(context);
    NSString *common = read(@"app/src/main/assets/shaders/common.glsl");
    NSRegularExpression *precision = [NSRegularExpression regularExpressionWithPattern:
        @"^precision[^;]+;" options:NSRegularExpressionAnchorsMatchLines error:nil];
    common = [precision stringByReplacingMatchesInString:common options:0
        range:NSMakeRange(0, common.length) withTemplate:@""];
    NSString *fragment = [@"#version 330 core\n" stringByAppendingString:common];
    fragment = [fragment stringByAppendingString:read(@"app/src/main/assets/shaders/surface_material.glsl")];
    fragment = [fragment stringByAppendingString:@"\n"
        "out vec4 color;\n"
        "void main() {\n"
        " vec2 cell = floor(gl_FragCoord.xy / vec2(320, 320));\n"
        " int index = int(cell.x + cell.y * 4.0);\n"
        " uint ids[8] = uint[8](14u, 54u, 70u, 5u, 13u, 4u, 30u, 8u);\n"
        " vec2 uv = mod(gl_FragCoord.xy, vec2(320)) / 320.0;\n"
        " vec3 position = vec3(uv.x * 4.0, 3.0, uv.y * 4.0);\n"
        " vec3 normal = vec3(0, 1, 0);\n"
        " if (index == 1) { position = vec3(uv.x * 4.0, 2.0 + uv.y, 0); normal = vec3(0, 0, 1); }\n"
        " if (index == 2 || index == 3 || index == 5 || index == 7) { position = vec3(uv * 4.0, 0); normal = vec3(0, 0, 1); }\n"
        " Material m = readSurfaceMaterial(ids[index], position, normal, 4.0 / 320.0);\n"
        " color = vec4(m.albedo, 1);\n"
        "}\n"];
    GLuint vert = compile(GL_VERTEX_SHADER, @"#version 330 core\nvoid main() { vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)); gl_Position = vec4(p * 2.0 - 1.0, 0, 1); }");
    GLuint frag = compile(GL_FRAGMENT_SHADER, fragment), program = glCreateProgram();
    glAttachShader(program, vert); glAttachShader(program, frag); glLinkProgram(program);
    GLint ok; glGetProgramiv(program, GL_LINK_STATUS, &ok); if (!ok) return 4;
    glUseProgram(program);
    GLuint atlas; glGenTextures(1, &atlas); glBindTexture(GL_TEXTURE_2D, atlas);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 192, 1, 0, GL_RGBA, GL_FLOAT, palette.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glUniform1i(glGetUniformLocation(program, "uPalette"), 0);
    GLuint fbo, texture, vao; glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glGenTextures(1, &texture); glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1280, 640, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return 5;
    glGenVertexArrays(1, &vao); glBindVertexArray(vao); glViewport(0, 0, 1280, 640);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    std::vector<unsigned char> pixels(1280 * 640 * 4);
    glReadPixels(0, 0, 1280, 640, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
    if (glGetError() != GL_NO_ERROR) return 6;
    NSBitmapImageRep *bitmap = [[NSBitmapImageRep alloc] initWithBitmapDataPlanes:nil pixelsWide:1280
        pixelsHigh:640 bitsPerSample:8 samplesPerPixel:4 hasAlpha:YES isPlanar:NO
        colorSpaceName:NSDeviceRGBColorSpace bytesPerRow:1280 * 4 bitsPerPixel:32];
    for (int y = 0; y < 640; ++y) memcpy(bitmap.bitmapData + y * 1280 * 4,
        pixels.data() + (639 - y) * 1280 * 4, 1280 * 4);
    NSData *png = [bitmap representationUsingType:NSBitmapImageFileTypePNG properties:@{}];
    if (![png writeToFile:[NSString stringWithUTF8String:argv[1]] atomically:YES]) return 7;
    printf("Rendered actual surface GLSL to %s\n", argv[1]);
    glDeleteProgram(program); glDeleteShader(vert); glDeleteShader(frag);
    glDeleteVertexArrays(1, &vao); glDeleteTextures(1, &atlas); glDeleteTextures(1, &texture);
    glDeleteFramebuffers(1, &fbo); CGLSetCurrentContext(nullptr); CGLDestroyContext(context);
    return 0;
} }
