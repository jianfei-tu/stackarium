param([string]$BaseUrl = 'http://127.0.0.1:8080')

$ErrorActionPreference = 'Stop'
$name = 'Spring Cloud 微服务实验'
$response = Invoke-WebRequest -Uri "$BaseUrl/api/v1/projects" -UseBasicParsing
$projects = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json
$existing = @($projects | Where-Object { $_.name -eq $name })
if ($existing.Count -gt 1) { throw '发现多个同名演示项目，请手工确认；脚本不会选择或覆盖。' }
if ($existing.Count -gt 0) {
    $project = $existing[0]
    $topology = Invoke-RestMethod -Uri "$BaseUrl/api/v1/projects/$($project.id)/topology"
    if ($topology.nodes.Count -ne 8 -or $topology.edges.Count -ne 10) {
        throw '同名项目已存在，但不是标准演示拓扑；请先手工确认，脚本不会覆盖。'
    }
    Write-Host "演示项目已存在：$($project.id)"
    exit 0
}

$body = @{ name = $name; description = 'Gateway、服务发现、缓存、消息与限流的可运行架构' } | ConvertTo-Json
$project = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/projects" -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
$ids = @{}
$nodes = @(
    @{ key = 'gateway'; type = 'gateway'; name = 'Gateway'; x = 80; y = 220; config = @{ port = '8080'; routePrefix = '/api' } },
    @{ key = 'order'; type = 'spring-service'; name = '订单服务'; x = 360; y = 220; config = @{ port = '8080'; serviceName = 'order-service' } },
    @{ key = 'inventory'; type = 'spring-service'; name = '库存服务'; x = 660; y = 100; config = @{ port = '8080'; serviceName = 'inventory-service' } },
    @{ key = 'consumer'; type = 'spring-service'; name = '通知消费者'; x = 660; y = 390; config = @{ port = '8080'; serviceName = 'notification-consumer' } },
    @{ key = 'nacos'; type = 'nacos'; name = 'Nacos'; x = 80; y = 520; config = @{ port = '8848'; namespace = 'public' } },
    @{ key = 'mysql'; type = 'mysql'; name = 'MySQL'; x = 960; y = 50; config = @{ port = '3306'; database = 'app' } },
    @{ key = 'redis'; type = 'redis'; name = 'Redis'; x = 960; y = 180; config = @{ port = '6379' } },
    @{ key = 'rabbit'; type = 'rabbitmq'; name = 'RabbitMQ'; x = 960; y = 390; config = @{ port = '5672'; queue = 'events' } }
) | ForEach-Object {
    $id = [guid]::NewGuid().ToString()
    $ids[$_.key] = $id
    @{ id = $id; componentType = $_.type; displayName = $_.name; x = $_.x; y = $_.y; config = $_.config }
}
$relations = @(
    @('gateway', 'order', 'PROXY'),
    @('gateway', 'nacos', 'DISCOVERY'),
    @('order', 'inventory', 'CALL'),
    @('order', 'nacos', 'DISCOVERY'),
    @('order', 'rabbit', 'MESSAGE'),
    @('inventory', 'mysql', 'CALL'),
    @('inventory', 'redis', 'CACHE'),
    @('inventory', 'nacos', 'DISCOVERY'),
    @('consumer', 'rabbit', 'MESSAGE'),
    @('consumer', 'nacos', 'DISCOVERY')
)
$edges = @($relations | ForEach-Object {
    @{ id = [guid]::NewGuid().ToString(); source = $ids[$_[0]]; target = $ids[$_[1]]; relationType = $_[2] }
})
$topologyBody = @{ expectedRevision = 0; nodes = $nodes; edges = $edges } | ConvertTo-Json -Depth 10
$saved = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/v1/projects/$($project.id)/topology" -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($topologyBody))
if ($saved.nodes.Count -ne 8 -or $saved.edges.Count -ne 10) { throw '演示拓扑保存后数量异常。' }
Write-Host "已创建演示项目：$($project.id)（8 节点 / 10 连接）"
