require 'sinatra'
require 'json'
require 'date'

DATA = {
  'AUDUSD' => 0.75,
  'AUDEUR' => 0.25
}

get '/convert' do
  pairs = params['pairs'].split(',')
  api_key = params['api_key']

  halt 400 if api_key.nil? or api_key.empty?

  pairs.map { |p| puts ">> [/convert] #{p.inspect} = #{DATA[p].inspect}"; { symbol: p, price: DATA[p], timestamp: Time.now.to_i } }.to_json
end

get '/symbols' do
  api_key = params['api_key']

  puts ">> #{api_key.inspect}"

  halt 400 if api_key.nil? or api_key.empty?

  DATA.keys.to_json
end
